package in.mahametro.competency.proctoring;

import in.mahametro.competency.delivery.Attempt;
import in.mahametro.competency.delivery.AttemptItem;
import in.mahametro.competency.questions.Question;
import in.mahametro.competency.questions.QuestionRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Response-time analysis: spots answers given too fast to have been read (rapid guessing).
 * Ported from Wise & Ma (2012) / Wise & Kong (2005) timing heuristics in Python pilot.
 */
@Service
public class TimingAnalysisService {

    public static final double RAPID_MIN_SECONDS = 3.0;
    public static final double RAPID_FLAG_SHARE = 0.10; // 10% threshold

    private final QuestionRepository questionRepository;

    public TimingAnalysisService(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    public static class ItemTiming {
        private final Long attemptItemId;
        private final Long questionId;
        private final int position;
        private final int timeSpentSeconds;
        private final double limitSeconds;
        private final boolean rapid;

        public ItemTiming(Long attemptItemId, Long questionId, int position, int timeSpentSeconds,
                          double limitSeconds, boolean rapid) {
            this.attemptItemId = attemptItemId;
            this.questionId = questionId;
            this.position = position;
            this.timeSpentSeconds = timeSpentSeconds;
            this.limitSeconds = limitSeconds;
            this.rapid = rapid;
        }

        public Long getAttemptItemId() { return attemptItemId; }
        public Long getQuestionId() { return questionId; }
        public int getPosition() { return position; }
        public int getTimeSpentSeconds() { return timeSpentSeconds; }
        public double getLimitSeconds() { return limitSeconds; }
        public boolean isRapid() { return rapid; }
    }

    public static class TimingSummary {
        private final int totalSeconds;
        private final int answeredCount;
        private final int rapidCount;
        private final double rte; // Response-time effort percentage
        private final boolean lowEffort;
        private final List<ItemTiming> itemTimings;

        public TimingSummary(int totalSeconds, int answeredCount, int rapidCount, double rte,
                             boolean lowEffort, List<ItemTiming> itemTimings) {
            this.totalSeconds = totalSeconds;
            this.answeredCount = answeredCount;
            this.rapidCount = rapidCount;
            this.rte = rte;
            this.lowEffort = lowEffort;
            this.itemTimings = itemTimings;
        }

        public int getTotalSeconds() { return totalSeconds; }
        public int getAnsweredCount() { return answeredCount; }
        public int getRapidCount() { return rapidCount; }
        public double getRte() { return rte; }
        public boolean isLowEffort() { return lowEffort; }
        public List<ItemTiming> getItemTimings() { return itemTimings; }
    }

    /**
     * Calculates minimal reading limit in seconds for a question stem.
     * Heuristic: RAPID_MIN_SECONDS (3s) + 0.15s per word, capped at 15s.
     */
    public double calculateThreshold(Question question) {
        if (question == null) {
            return RAPID_MIN_SECONDS;
        }
        String stem = question.getStemEn() != null && !question.getStemEn().isBlank()
                ? question.getStemEn()
                : (question.getStemMr() != null ? question.getStemMr() : "");
        String[] words = stem.trim().isEmpty() ? new String[0] : stem.trim().split("\\s+");
        double limit = RAPID_MIN_SECONDS + (0.15 * words.length);
        return Math.min(15.0, Math.round(limit * 10.0) / 10.0);
    }

    public TimingSummary analyse(Attempt attempt, List<AttemptItem> items) {
        Map<Long, Question> questionMap = new HashMap<>();
        for (AttemptItem item : items) {
            if (!questionMap.containsKey(item.getQuestionId())) {
                questionRepository.findById(item.getQuestionId()).ifPresent(q -> questionMap.put(q.getId(), q));
            }
        }

        int totalSeconds = 0;
        int answeredCount = 0;
        int rapidCount = 0;
        List<ItemTiming> itemTimings = new ArrayList<>();

        for (AttemptItem ai : items) {
            Question q = questionMap.get(ai.getQuestionId());
            double limit = calculateThreshold(q);
            int timeSpent = ai.getTimeSpentSeconds();
            totalSeconds += timeSpent;

            boolean hasAnswer = (ai.getSelectedOptions() != null && !ai.getSelectedOptions().isBlank())
                    || (ai.getAnsweredText() != null && !ai.getAnsweredText().isBlank());

            boolean isRapid = hasAnswer && timeSpent < limit;
            if (hasAnswer) {
                answeredCount++;
                if (isRapid) {
                    rapidCount++;
                }
            }

            itemTimings.add(new ItemTiming(ai.getId(), ai.getQuestionId(), ai.getPosition(), timeSpent, limit, isRapid));
        }

        double rte = answeredCount > 0
                ? Math.round((100.0 * (answeredCount - rapidCount) / answeredCount) * 10.0) / 10.0
                : 100.0;

        boolean lowEffort = answeredCount > 0 && (((double) rapidCount / answeredCount) > RAPID_FLAG_SHARE);

        return new TimingSummary(totalSeconds, answeredCount, rapidCount, rte, lowEffort, itemTimings);
    }
}
