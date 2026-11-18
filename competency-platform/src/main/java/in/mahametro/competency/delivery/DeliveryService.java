package in.mahametro.competency.delivery;

import in.mahametro.competency.core.AuditService;
import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.questions.QuestionOption;
import in.mahametro.competency.questions.QuestionOptionRepository;
import in.mahametro.competency.scoring.CompetencyScore;
import in.mahametro.competency.scoring.CompetencyScoreRepository;
import in.mahametro.competency.tests.AssessmentTest;
import in.mahametro.competency.tests.AssessmentTestRepository;
import in.mahametro.competency.tests.TestService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles test taking, server-side timer management, answer autosaving,
 * timeout auto-submission, and automatic scoring (Goal 3 & Goal 5).
 */
@Service
public class DeliveryService {

    private final AssessmentTestRepository testRepository;
    private final TestService testService;
    private final AttemptRepository attemptRepository;
    private final AttemptItemRepository attemptItemRepository;
    private final QuestionOptionRepository optionRepository;
    private final CompetencyScoreRepository competencyScoreRepository;
    private final PaperAssemblyService paperAssemblyService;
    private final in.mahametro.competency.proctoring.TimingAnalysisService timingAnalysisService;
    private final in.mahametro.competency.development.PrescriptionService prescriptionService;
    private final in.mahametro.competency.reporting.ReportingService reportingService;
    private final AutosaveBufferService autosaveBufferService;
    private final AuditService audit;

    public DeliveryService(AssessmentTestRepository testRepository, TestService testService,
            AttemptRepository attemptRepository, AttemptItemRepository attemptItemRepository,
            QuestionOptionRepository optionRepository, CompetencyScoreRepository competencyScoreRepository,
            PaperAssemblyService paperAssemblyService,
            in.mahametro.competency.proctoring.TimingAnalysisService timingAnalysisService,
            in.mahametro.competency.development.PrescriptionService prescriptionService,
            in.mahametro.competency.reporting.ReportingService reportingService,
            AutosaveBufferService autosaveBufferService,
            AuditService audit) {
        this.testRepository = testRepository;
        this.testService = testService;
        this.attemptRepository = attemptRepository;
        this.attemptItemRepository = attemptItemRepository;
        this.optionRepository = optionRepository;
        this.competencyScoreRepository = competencyScoreRepository;
        this.paperAssemblyService = paperAssemblyService;
        this.timingAnalysisService = timingAnalysisService;
        this.prescriptionService = prescriptionService;
        this.reportingService = reportingService;
        this.autosaveBufferService = autosaveBufferService;
        this.audit = audit;
    }


    /**
     * Starts a new test attempt or resumes an active one.
     */
    @Transactional
    public Attempt startOrResumeAttempt(Long testId, Employee candidate) {
        AssessmentTest test = testRepository.findById(testId)
                .orElseThrow(() -> new IllegalArgumentException("Test not found: " + testId));

        if (!testService.isEligible(test, candidate)) {
            throw new IllegalStateException("You are not eligible to take this test.");
        }

        // Check for an existing in-progress attempt to resume
        Optional<Attempt> existing = attemptRepository.findFirstByTestIdAndEmployeeIdAndStatus(
                test.getId(), candidate.getId(), Attempt.STATUS_IN_PROGRESS);
        if (existing.isPresent()) {
            Attempt attempt = existing.get();
            if (attempt.isExpired()) {
                submitAttempt(attempt.getId(), candidate);
            } else {
                return attempt; // resume active attempt
            }
        }

        // Check attempt limits
        if (test.getMaxAttempts() > 0) {
            long used = attemptRepository.countByTestIdAndEmployeeIdAndStatusNot(
                    test.getId(), candidate.getId(), Attempt.STATUS_VOID);
            if (used >= test.getMaxAttempts()) {
                throw new IllegalStateException("Maximum test attempts (" + test.getMaxAttempts() + ") reached.");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.plusMinutes(test.getDurationMinutes());
        if (test.getEndAt() != null && test.getEndAt().isBefore(deadline)) {
            deadline = test.getEndAt();
        }

        long seed = ((long) candidate.getEmpId().hashCode() << 16) ^ System.currentTimeMillis();
        Attempt attempt = new Attempt(test.getId(), candidate.getId(), test.getKind(), seed, deadline);
        attempt = attemptRepository.save(attempt);

        List<AttemptItem> items = paperAssemblyService.assemble(test, candidate, attempt.getId(), seed);
        if (items.isEmpty()) {
            throw new IllegalStateException("No questions available in the bank for this test.");
        }
        attemptItemRepository.saveAll(items);

        audit.record(candidate.getEmpId(), "attempt_started", "attempt", attempt.getId().toString(), test.getCode());
        return attempt;
    }

    /**
     * Autosaves a candidate's answer for a question.
     */
    @Transactional
    public void recordAnswer(Long attemptId, Employee candidate, int position, String selectedOptions,
            String answeredText, int timeSpentSeconds) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Attempt not found"));

        if (!attempt.getEmployeeId().equals(candidate.getId())) {
            throw new IllegalStateException("Unauthorized attempt access.");
        }

        if (!attempt.isInProgress()) {
            return;
        }

        if (attempt.isExpired()) {
            submitAttempt(attemptId, candidate);
            return;
        }

        autosaveBufferService.bufferAnswer(attemptId, position, selectedOptions, answeredText, timeSpentSeconds);
    }

    /**
     * Submits an attempt and auto-marks it (Goal 3 + Goal 5).
     */
    @Transactional
    public Attempt submitAttempt(Long attemptId, Employee candidate) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Attempt not found: " + attemptId));

        if (!attempt.getEmployeeId().equals(candidate.getId())) {
            throw new IllegalStateException("Unauthorized attempt access.");
        }

        if (!attempt.isInProgress()) {
            return attempt;
        }

        // Ensure any buffered autosaves are flushed to the database prior to scoring
        autosaveBufferService.flushAttempt(attemptId);

        AssessmentTest test = testRepository.findById(attempt.getTestId()).orElse(null);
        List<AttemptItem> items = attemptItemRepository.findByAttemptIdOrderByPositionAsc(attemptId);

        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal totalMaxScore = BigDecimal.ZERO;

        Map<String, List<BigDecimal>> compScores = new HashMap<>();

        for (AttemptItem item : items) {
            BigDecimal maxMarks = item.getMarks() != null ? item.getMarks() : BigDecimal.ONE;
            totalMaxScore = totalMaxScore.add(maxMarks);

            List<QuestionOption> options = optionRepository.findByQuestionIdOrderByOrdAsc(item.getQuestionId());
            Set<Long> correctOptionIds = options.stream()
                    .filter(QuestionOption::isCorrect)
                    .map(QuestionOption::getId)
                    .collect(Collectors.toSet());

            Set<Long> candidateSelectedIds = new HashSet<>();
            if (item.getSelectedOptions() != null && !item.getSelectedOptions().isBlank()) {
                for (String s : item.getSelectedOptions().split(",")) {
                    s = s.trim();
                    if (!s.isEmpty()) {
                        try {
                            candidateSelectedIds.add(Long.parseLong(s));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }

            boolean isCorrect = !correctOptionIds.isEmpty() && correctOptionIds.equals(candidateSelectedIds);
            item.setIsCorrect(isCorrect);
            BigDecimal awarded = isCorrect ? maxMarks : BigDecimal.ZERO;
            item.setAwardedScore(awarded);
            totalScore = totalScore.add(awarded);

            // Record per-competency ratio for measured competency scores
            BigDecimal ratio = maxMarks.compareTo(BigDecimal.ZERO) > 0
                    ? awarded.divide(maxMarks, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            compScores.computeIfAbsent(item.getCompetencyCode(), k -> new ArrayList<>()).add(ratio);
        }
        attemptItemRepository.saveAll(items);

        attempt.setSubmittedAt(LocalDateTime.now());
        attempt.setScore(totalScore);
        attempt.setMaxScore(totalMaxScore);

        BigDecimal percent = BigDecimal.ZERO;
        if (totalMaxScore.compareTo(BigDecimal.ZERO) > 0) {
            percent = totalScore.multiply(BigDecimal.valueOf(100)).divide(totalMaxScore, 2, RoundingMode.HALF_UP);
        }
        attempt.setPercent(percent);

        // Goal 4: Proctoring & timing integrity checks
        List<String> flags = new ArrayList<>();
        if (test != null && !test.isPractice() && attempt.getTabSwitches() > 3) {
            flags.add("Exceeded tab switch limit (" + attempt.getTabSwitches() + " switches)");
        }
        in.mahametro.competency.proctoring.TimingAnalysisService.TimingSummary timing = timingAnalysisService.analyse(attempt, items);
        if (timing.isLowEffort()) {
            flags.add("Rapid guessing detected (Effort RTE " + timing.getRte() + "%, "
                    + timing.getRapidCount() + " rapid answers of " + timing.getAnsweredCount() + ")");
        }

        if (!flags.isEmpty()) {
            attempt.setStatus(Attempt.STATUS_FLAGGED);
            attempt.setFlagReason(String.join(" | ", flags));
        } else {
            attempt.setStatus(Attempt.STATUS_SCORED);
            if (test != null && !test.isPractice()) {
                publishScoresFromMap(compScores, candidate, test, attempt.getId());
            }
        }
        attempt = attemptRepository.save(attempt);

        audit.record(candidate.getEmpId(), "attempt_submitted", "attempt", attempt.getId().toString(),
                "status=" + attempt.getStatus() + " score=" + totalScore + "/" + totalMaxScore);
        return attempt;
    }

    private void publishScoresFromMap(Map<String, List<BigDecimal>> compScores, Employee candidate,
                                      AssessmentTest test, Long attemptId) {
        for (Map.Entry<String, List<BigDecimal>> entry : compScores.entrySet()) {
            String compCode = entry.getKey();
            List<BigDecimal> ratios = entry.getValue();
            double avgRatio = ratios.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0.0);
            double measuredScore = Math.min(100.0, Math.max(0.0, avgRatio * 100.0));
            competencyScoreRepository.save(new CompetencyScore(
                    candidate.getId(),
                    compCode,
                    test.getKind(),
                    "knowledge",
                    measuredScore,
                    "auto_test_" + attemptId
            ));
        }

        // Goal 6: Automatically generate or update Individual Development Plan (IDP)
        prescriptionService.generatePlan(candidate, test.getKind());

        // Goal 8: Refresh pre-aggregated profile summary
        reportingService.refreshSummary(candidate.getId());
    }


    @Transactional
    public void publishAttemptScores(Attempt attempt, AssessmentTest test, Employee candidate) {
        List<AttemptItem> items = attemptItemRepository.findByAttemptIdOrderByPositionAsc(attempt.getId());
        Map<String, List<BigDecimal>> compScores = new java.util.HashMap<>();
        for (AttemptItem item : items) {
            BigDecimal maxMarks = item.getMarks() != null ? item.getMarks() : BigDecimal.ONE;
            BigDecimal awarded = item.getAwardedScore() != null ? item.getAwardedScore() : BigDecimal.ZERO;
            BigDecimal ratio = maxMarks.compareTo(BigDecimal.ZERO) > 0
                    ? awarded.divide(maxMarks, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            compScores.computeIfAbsent(item.getCompetencyCode(), k -> new ArrayList<>()).add(ratio);
        }
        publishScoresFromMap(compScores, candidate, test, attempt.getId());
    }

    public Attempt findById(Long id) {
        return attemptRepository.findById(id).orElse(null);
    }

    public List<AttemptItem> getItems(Long attemptId) {
        return attemptItemRepository.findByAttemptIdOrderByPositionAsc(attemptId);
    }
}
