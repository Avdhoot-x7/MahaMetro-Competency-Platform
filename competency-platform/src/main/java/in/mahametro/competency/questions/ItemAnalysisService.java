package in.mahametro.competency.questions;

import in.mahametro.competency.delivery.Attempt;
import in.mahametro.competency.delivery.AttemptItem;
import in.mahametro.competency.delivery.AttemptItemRepository;
import in.mahametro.competency.delivery.AttemptRepository;
import in.mahametro.competency.tests.AssessmentTest;
import in.mahametro.competency.tests.AssessmentTestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Item Analysis Engine implementing empirical psychometric analysis according to Section 9 of the Spec:
 * - Facility index p = (number answering correctly) / (number answering)
 * - Point-biserial discrimination r_pb = ((X_bar_1 - X_bar_0) / s_X) * sqrt(p * (1 - p))
 * - Distractor frequency and discrimination
 * - Automated flagging and action recommendations:
 *     p < 0.30 (Too hard) -> Back to panel; check key
 *     p > 0.85 (Too easy) -> Back to panel
 *     r < 0.20 (Weak discrimination) -> Back to panel
 *     r < 0.00 (Negative discrimination) -> Remove from exams at once; likely wrong key
 *     distractor < 5% -> Not working; replace option
 *     distractor with positive rest correlation -> Strong candidates choose it; check for 2nd right answer
 */
@Service
public class ItemAnalysisService {

    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository optionRepository;
    private final AttemptItemRepository attemptItemRepository;
    private final AttemptRepository attemptRepository;
    private final AssessmentTestRepository testRepository;

    public ItemAnalysisService(QuestionRepository questionRepository,
                               QuestionOptionRepository optionRepository,
                               AttemptItemRepository attemptItemRepository,
                               AttemptRepository attemptRepository,
                               AssessmentTestRepository testRepository) {
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.attemptItemRepository = attemptItemRepository;
        this.attemptRepository = attemptRepository;
        this.testRepository = testRepository;
    }

    public static class DistractorStat {
        private final Long optionId;
        private final String label;
        private final String textEn;
        private final boolean correct;
        private final int chooseCount;
        private final double chooseShare;
        private final double correlation;
        private final String misconception;
        private final String flag;

        public DistractorStat(Long optionId, String label, String textEn, boolean correct,
                              int chooseCount, double chooseShare, double correlation,
                              String misconception, String flag) {
            this.optionId = optionId;
            this.label = label;
            this.textEn = textEn;
            this.correct = correct;
            this.chooseCount = chooseCount;
            this.chooseShare = chooseShare;
            this.correlation = correlation;
            this.misconception = misconception;
            this.flag = flag;
        }

        public Long getOptionId() { return optionId; }
        public String getLabel() { return label; }
        public String getTextEn() { return textEn; }
        public boolean isCorrect() { return correct; }
        public int getChooseCount() { return chooseCount; }
        public double getChooseShare() { return chooseShare; }
        public double getCorrelation() { return correlation; }
        public String getMisconception() { return misconception; }
        public String getFlag() { return flag; }
    }

    public static class ItemAnalysisResult {
        private final Long questionId;
        private final String competencyCode;
        private final String stemEn;
        private final String qtype;
        private final String difficultyBand;
        private final String status;
        private final int n;
        private final Double pValue;
        private final Double pointBiserial;
        private final List<DistractorStat> distractors;
        private final List<String> flags;
        private final String action;
        private final boolean negativeDiscrimination;

        public ItemAnalysisResult(Long questionId, String competencyCode, String stemEn,
                                  String qtype, String difficultyBand, String status, int n,
                                  Double pValue, Double pointBiserial, List<DistractorStat> distractors,
                                  List<String> flags, String action, boolean negativeDiscrimination) {
            this.questionId = questionId;
            this.competencyCode = competencyCode;
            this.stemEn = stemEn;
            this.qtype = qtype;
            this.difficultyBand = difficultyBand;
            this.status = status;
            this.n = n;
            this.pValue = pValue;
            this.pointBiserial = pointBiserial;
            this.distractors = distractors;
            this.flags = flags;
            this.action = action;
            this.negativeDiscrimination = negativeDiscrimination;
        }

        public Long getQuestionId() { return questionId; }
        public String getCompetencyCode() { return competencyCode; }
        public String getStemEn() { return stemEn; }
        public String getQtype() { return qtype; }
        public String getDifficultyBand() { return difficultyBand; }
        public String getStatus() { return status; }
        public int getN() { return n; }
        public Double getPValue() { return pValue; }
        public Double getPointBiserial() { return pointBiserial; }
        public List<DistractorStat> getDistractors() { return distractors; }
        public List<String> getFlags() { return flags; }
        public String getAction() { return action; }
        public boolean isNegativeDiscrimination() { return negativeDiscrimination; }
    }

    /**
     * Runs item analysis over all questions, updates DB stats (p_value, point_biserial, stats_n),
     * auto-removes negative discrimination questions (r < 0) from active exams, and returns the results.
     */
    @Transactional
    public List<ItemAnalysisResult> runAllItemAnalysis() {
        List<Question> questions = questionRepository.findAll();
        List<ItemAnalysisResult> results = new ArrayList<>();

        for (Question q : questions) {
            ItemAnalysisResult res = analyzeQuestion(q, true);
            results.add(res);
        }

        return results;
    }

    /**
     * Analyzes a single question's psychometrics.
     */
    @Transactional
    public ItemAnalysisResult analyzeQuestion(Question question, boolean persistStats) {
        List<AttemptItem> rawItems = attemptItemRepository.findByQuestionId(question.getId());

        // Filter to scored, non-practice attempts only
        Map<Long, Attempt> attemptMap = new HashMap<>();
        List<AttemptItem> validItems = new ArrayList<>();

        for (AttemptItem item : rawItems) {
            Attempt att = attemptMap.computeIfAbsent(item.getAttemptId(),
                    id -> attemptRepository.findById(id).orElse(null));

            if (att != null && !AssessmentTest.KIND_PRACTICE.equalsIgnoreCase(att.getKind())
                    && (Attempt.STATUS_SCORED.equalsIgnoreCase(att.getStatus())
                    || Attempt.STATUS_SUBMITTED.equalsIgnoreCase(att.getStatus()))) {
                validItems.add(item);
            }
        }

        int n = validItems.size();
        List<QuestionOption> options = optionRepository.findByQuestionIdOrderByOrdAsc(question.getId());

        if (n == 0) {
            return new ItemAnalysisResult(question.getId(), question.getCompetencyCode(),
                    question.getStemEn(), question.getQtype(), question.getDifficultyBand(),
                    question.getStatus(), 0, null, null, Collections.emptyList(),
                    List.of("No test attempts yet"), "Collect minimum 20 attempts for calibrated psychometrics", false);
        }

        // 1. Calculate p-value (facility)
        double totalItemScore = 0.0;
        int correctCount = 0;
        List<Double> itemScores = new ArrayList<>(n);
        List<Double> restScores = new ArrayList<>(n);
        List<Boolean> isCorrectList = new ArrayList<>(n);

        for (AttemptItem item : validItems) {
            Attempt att = attemptMap.get(item.getAttemptId());
            double iScore = item.getAwardedScore() != null ? item.getAwardedScore().doubleValue()
                    : (Boolean.TRUE.equals(item.getIsCorrect()) ? 1.0 : 0.0);
            boolean correct = Boolean.TRUE.equals(item.getIsCorrect()) || iScore > 0;

            if (correct) {
                correctCount++;
            }
            totalItemScore += iScore;
            itemScores.add(iScore);
            isCorrectList.add(correct);

            // Rest score = attempt total score - this item score
            double totalScore = att != null && att.getScore() != null ? att.getScore().doubleValue() : iScore;
            restScores.add(Math.max(0.0, totalScore - iScore));
        }

        double p = (double) correctCount / n;
        // Clamp p to [0.0, 1.0]
        p = Math.max(0.0, Math.min(1.0, p));

        // 2. Calculate point-biserial discrimination r_pb
        Double rPb = null;
        if (n >= 2) {
            double meanRest = restScores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double sumSqDiff = 0.0;
            for (double x : restScores) {
                sumSqDiff += (x - meanRest) * (x - meanRest);
            }
            double sX = Math.sqrt(sumSqDiff / (n - 1));

            double sumRest1 = 0.0;
            int n1 = 0;
            double sumRest0 = 0.0;
            int n0 = 0;

            for (int i = 0; i < n; i++) {
                if (isCorrectList.get(i)) {
                    sumRest1 += restScores.get(i);
                    n1++;
                } else {
                    sumRest0 += restScores.get(i);
                    n0++;
                }
            }

            if (sX > 0.0001 && n1 > 0 && n0 > 0 && p > 0 && p < 1.0) {
                double meanRest1 = sumRest1 / n1;
                double meanRest0 = sumRest0 / n0;
                rPb = ((meanRest1 - meanRest0) / sX) * Math.sqrt(p * (1.0 - p));
                // Clamp to [-1.0, 1.0]
                rPb = Math.max(-1.0, Math.min(1.0, rPb));
            } else {
                rPb = 0.0;
            }
        }

        // 3. Distractor Analysis
        List<DistractorStat> distractorStats = new ArrayList<>();
        char optLabel = 'A';
        for (QuestionOption opt : options) {
            String optIdStr = String.valueOf(opt.getId());
            int chosen = 0;
            double sumRestChosen = 0.0;
            int nChosen = 0;
            double sumRestNotChosen = 0.0;
            int nNotChosen = 0;

            for (int i = 0; i < n; i++) {
                AttemptItem it = validItems.get(i);
                boolean selected = it.getSelectedOptions() != null
                        && Arrays.asList(it.getSelectedOptions().split(",")).contains(optIdStr);

                if (selected) {
                    chosen++;
                    sumRestChosen += restScores.get(i);
                    nChosen++;
                } else {
                    sumRestNotChosen += restScores.get(i);
                    nNotChosen++;
                }
            }

            double share = (double) chosen / n;
            double optCorr = 0.0;
            if (n >= 2 && nChosen > 0 && nNotChosen > 0) {
                double meanC = sumRestChosen / nChosen;
                double meanNC = sumRestNotChosen / nNotChosen;
                double meanRest = restScores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                double sumSq = 0.0;
                for (double x : restScores) sumSq += (x - meanRest) * (x - meanRest);
                double s = Math.sqrt(sumSq / (n - 1));
                if (s > 0.0001) {
                    optCorr = ((meanC - meanNC) / s) * Math.sqrt(share * (1.0 - share));
                }
            }

            String dFlag = "";
            if (!opt.isCorrect()) {
                if (n >= 20 && share < 0.05) {
                    dFlag = "Not working (< 5% chosen)";
                } else if (n >= 20 && optCorr > 0.10) {
                    dFlag = "Strong candidates choose it (+corr)";
                }
            }

            distractorStats.add(new DistractorStat(opt.getId(), String.valueOf(optLabel++),
                    opt.getTextEn(), opt.isCorrect(), chosen, share, optCorr,
                    opt.getMisconception(), dFlag));
        }

        // 4. Flags and actions according to Section 9 rules
        List<String> flags = new ArrayList<>();
        String action = "Retain in exam pool (Psychometrics healthy)";
        boolean negDiscrim = false;

        if (n < 20) {
            flags.add(String.format("Preliminary sample (N = %d < 20 benchmark)", n));
        }

        if (p < 0.30) {
            flags.add("Too hard (p < 0.30)");
            action = "Back to panel; first check the key is right";
        } else if (p > 0.85) {
            flags.add("Too easy (p > 0.85)");
            action = "Back to panel for revision";
        }

        if (rPb != null) {
            if (rPb < 0.0) {
                flags.add("Negative discrimination (r < 0)");
                action = "Remove from exams at once; likely wrong key";
                negDiscrim = true;
            } else if (rPb < 0.20 && n >= 20) {
                flags.add("Weak discrimination (r < 0.20)");
                if (!action.contains("Remove")) {
                    action = "Back to panel for review";
                }
            }
        }

        for (DistractorStat ds : distractorStats) {
            if (!ds.isCorrect() && ds.getFlag() != null && !ds.getFlag().isBlank()) {
                flags.add(String.format("Option %s: %s", ds.getLabel(), ds.getFlag()));
            }
        }

        // 5. Update Question entity in DB if requested
        if (persistStats) {
            question.setPValue(Math.round(p * 1000.0) / 1000.0);
            if (rPb != null) {
                question.setPointBiserial(Math.round(rPb * 1000.0) / 1000.0);
            }
            question.setStatsN(n);

            // If negative discrimination and n >= 20, remove from exams at once
            if (negDiscrim && n >= 20) {
                question.setStatus(Question.STATUS_REVIEW);
                String warning = "PSYCHOMETRIC ALERT: Negative discrimination (r = " + String.format("%.2f", rPb)
                        + "). Automatically returned to review pool.";
                if (question.getQcWarnings() == null || question.getQcWarnings().isBlank()) {
                    question.setQcWarnings(warning);
                } else if (!question.getQcWarnings().contains("PSYCHOMETRIC ALERT")) {
                    question.setQcWarnings(question.getQcWarnings() + "\n" + warning);
                }
            }

            questionRepository.save(question);
        }

        return new ItemAnalysisResult(question.getId(), question.getCompetencyCode(),
                question.getStemEn(), question.getQtype(), question.getDifficultyBand(),
                question.getStatus(), n, p, rPb, distractorStats, flags, action, negDiscrim);
    }
}
