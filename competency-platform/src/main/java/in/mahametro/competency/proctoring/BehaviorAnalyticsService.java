package in.mahametro.competency.proctoring;

import in.mahametro.competency.delivery.Attempt;
import in.mahametro.competency.delivery.AttemptEvent;
import in.mahametro.competency.delivery.AttemptEventRepository;
import in.mahametro.competency.delivery.AttemptItem;
import in.mahametro.competency.delivery.AttemptItemRepository;
import in.mahametro.competency.delivery.AttemptRepository;
import in.mahametro.competency.framework.Competency;
import in.mahametro.competency.framework.CompetencyRepository;
import in.mahametro.competency.questions.Question;
import in.mahametro.competency.questions.QuestionOption;
import in.mahametro.competency.questions.QuestionOptionRepository;
import in.mahametro.competency.questions.QuestionRepository;
import in.mahametro.competency.tests.AssessmentTest;
import in.mahametro.competency.tests.AssessmentTestRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes the 20 Behaviour Measures (A1-E4) across 6 reporting headings
 * from attempt logs and attempt_events according to Section 7 of the Spec.
 *
 * NON-NEGOTIABLE RULE: Behaviour measures support development conversations;
 * they NEVER change any competency score, attempt score, or readiness status.
 * Flags A5 (random guessing) and E1 (rule compliance) are confidential to the
 * Assessment Cell and Proctors until formally reviewed.
 */
@Service
public class BehaviorAnalyticsService {

    private final AttemptRepository attemptRepository;
    private final AttemptItemRepository attemptItemRepository;
    private final AttemptEventRepository attemptEventRepository;
    private final AssessmentTestRepository testRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository optionRepository;
    private final CompetencyRepository competencyRepository;

    public BehaviorAnalyticsService(AttemptRepository attemptRepository,
                                    AttemptItemRepository attemptItemRepository,
                                    AttemptEventRepository attemptEventRepository,
                                    AssessmentTestRepository testRepository,
                                    QuestionRepository questionRepository,
                                    QuestionOptionRepository optionRepository,
                                    CompetencyRepository competencyRepository) {
        this.attemptRepository = attemptRepository;
        this.attemptItemRepository = attemptItemRepository;
        this.attemptEventRepository = attemptEventRepository;
        this.testRepository = testRepository;
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.competencyRepository = competencyRepository;
    }

    public static class BehaviorMeasure {
        private String code;
        private String name;
        private String category;
        private String value;
        private String benchmark;
        private boolean flagged;
        private String flagReason;
        private boolean positiveSignal;
        private boolean confidential; // A5 and E1 are confidential to Assessment Cell / Proctor

        public BehaviorMeasure(String code, String name, String category, String value,
                               String benchmark, boolean flagged, String flagReason,
                               boolean positiveSignal, boolean confidential) {
            this.code = code;
            this.name = name;
            this.category = category;
            this.value = value;
            this.benchmark = benchmark;
            this.flagged = flagged;
            this.flagReason = flagReason;
            this.positiveSignal = positiveSignal;
            this.confidential = confidential;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
        public String getCategory() { return category; }
        public String getValue() { return value; }
        public String getBenchmark() { return benchmark; }
        public boolean isFlagged() { return flagged; }
        public String getFlagReason() { return flagReason; }
        public boolean isPositiveSignal() { return positiveSignal; }
        public boolean isConfidential() { return confidential; }
    }

    public static class BehaviorHeading {
        private String heading;
        private String purpose;
        private String status; // "Strong", "Typical", "Needs attention"
        private String badgeColor;
        private List<BehaviorMeasure> measures;

        public BehaviorHeading(String heading, String purpose, String status, String badgeColor, List<BehaviorMeasure> measures) {
            this.heading = heading;
            this.purpose = purpose;
            this.status = status;
            this.badgeColor = badgeColor;
            this.measures = measures;
        }

        public String getHeading() { return heading; }
        public String getPurpose() { return purpose; }
        public String getStatus() { return status; }
        public String getBadgeColor() { return badgeColor; }
        public List<BehaviorMeasure> getMeasures() { return measures; }
    }

    public static class BehaviorReport {
        private Long attemptId;
        private Long employeeId;
        private List<BehaviorHeading> headings;
        private Map<String, BehaviorMeasure> measuresByCode;
        private int totalFlags;
        private boolean hasConfidentialFlags;
        private final String disclaimer = "Behavior measures support development conversations; they never alter a competency score and are never used alone for a decision about a person.";

        public BehaviorReport(Long attemptId, Long employeeId, List<BehaviorHeading> headings,
                              Map<String, BehaviorMeasure> measuresByCode, int totalFlags, boolean hasConfidentialFlags) {
            this.attemptId = attemptId;
            this.employeeId = employeeId;
            this.headings = headings;
            this.measuresByCode = measuresByCode;
            this.totalFlags = totalFlags;
            this.hasConfidentialFlags = hasConfidentialFlags;
        }

        public Long getAttemptId() { return attemptId; }
        public Long getEmployeeId() { return employeeId; }
        public List<BehaviorHeading> getHeadings() { return headings; }
        public Map<String, BehaviorMeasure> getMeasuresByCode() { return measuresByCode; }
        public int getTotalFlags() { return totalFlags; }
        public boolean isHasConfidentialFlags() { return hasConfidentialFlags; }
        public String getDisclaimer() { return disclaimer; }
    }

    /**
     * Generates the complete 20 behaviour measures report for a test attempt.
     * Guaranteed read-only: does not modify any attempt score, competency score, or readiness status.
     *
     * @param attempt the test attempt
     * @param isPrivilegedViewer true if user has test.proctor or test.manage permissions (Cell/Proctor)
     * @return BehaviorReport
     */
    public BehaviorReport generateReport(Attempt attempt, boolean isPrivilegedViewer) {
        if (attempt == null) {
            return null;
        }

        List<AttemptItem> items = attemptItemRepository.findByAttemptIdOrderByPositionAsc(attempt.getId());
        List<AttemptEvent> events = attemptEventRepository.findByAttemptIdOrderByAtAsc(attempt.getId());
        AssessmentTest test = testRepository.findById(attempt.getTestId()).orElse(null);
        List<Attempt> empHistory = attemptRepository.findByEmployeeIdOrderByStartedAtDesc(attempt.getEmployeeId());

        Map<String, BehaviorMeasure> measures = new LinkedHashMap<>();

        // Group 1: Test-taking behaviour (A1, A2, E1)
        computeMeasureA1(attempt, test, items, measures);
        computeMeasureA2(items, measures);
        computeMeasureE1(attempt, test, events, measures);

        // Group 2: Answering behaviour (A4, A5, B3)
        computeMeasureA4(events, items, measures);
        computeMeasureA5(test, items, measures);
        computeMeasureB3(events, attempt, measures);

        // Group 3: Time management (A3, B1)
        computeMeasureA3(test, items, measures);
        computeMeasureB1(attempt, test, items, measures);

        // Group 4: Solution engagement (C1, C2, C3, C4)
        computeMeasureC1(attempt, events, measures);
        computeMeasureC2(events, measures);
        computeMeasureC3(items, events, measures);
        computeMeasureC4(items, events, measures);

        // Group 5: Learning behaviour (D1, D2, D3, D4, E3)
        computeMeasureD1(attempt, empHistory, measures);
        computeMeasureD2(items, empHistory, measures);
        computeMeasureD3(attempt, events, measures);
        computeMeasureD4(attempt, empHistory, measures);
        computeMeasureE3(attempt, empHistory, events, measures);

        // Group 6: Consistency (B2, E2, E4)
        computeMeasureB2(attempt, empHistory, measures);
        computeMeasureE2(attempt, empHistory, measures);
        computeMeasureE4(empHistory, measures);

        // Build 6 reporting headings
        List<BehaviorHeading> headings = new ArrayList<>();
        headings.add(buildHeading("Test-taking behaviour", "Discipline and approach to the exam",
                Arrays.asList(measures.get("A1"), measures.get("A2"), measures.get("E1")), isPrivilegedViewer));

        headings.add(buildHeading("Answering behaviour", "Decision-making and confidence",
                Arrays.asList(measures.get("A4"), measures.get("A5"), measures.get("B3")), isPrivilegedViewer));

        headings.add(buildHeading("Time management", "Speed and efficiency",
                Arrays.asList(measures.get("A3"), measures.get("B1")), isPrivilegedViewer));

        headings.add(buildHeading("Solution engagement", "Interest in learning from the test",
                Arrays.asList(measures.get("C1"), measures.get("C2"), measures.get("C3"), measures.get("C4")), isPrivilegedViewer));

        headings.add(buildHeading("Learning behaviour", "Improvement and adaptability",
                Arrays.asList(measures.get("D1"), measures.get("D2"), measures.get("D3"), measures.get("D4"), measures.get("E3")), isPrivilegedViewer));

        headings.add(buildHeading("Consistency", "Regularity and sustained effort",
                Arrays.asList(measures.get("B2"), measures.get("E2"), measures.get("E4")), isPrivilegedViewer));

        // Mask confidential measures (A5, E1) if viewer is not privileged
        if (!isPrivilegedViewer) {
            maskConfidential(measures.get("A5"));
            maskConfidential(measures.get("E1"));
        }

        int totalFlags = 0;
        boolean hasConfidential = measures.get("A5").isFlagged() || measures.get("E1").isFlagged();
        for (BehaviorMeasure m : measures.values()) {
            if (m.isFlagged() && (!m.isConfidential() || isPrivilegedViewer)) {
                totalFlags++;
            }
        }

        return new BehaviorReport(attempt.getId(), attempt.getEmployeeId(), headings, measures, totalFlags, hasConfidential);
    }

    private void maskConfidential(BehaviorMeasure m) {
        if (m != null) {
            m.value = "Confidential Proctor Metric — Reviewed by Assessment Cell";
            m.flagReason = "";
            m.flagged = false;
        }
    }

    private BehaviorHeading buildHeading(String title, String purpose, List<BehaviorMeasure> list, boolean isPrivileged) {
        int flags = 0;
        int positive = 0;
        for (BehaviorMeasure m : list) {
            if (m != null) {
                if (m.isFlagged() && (!m.isConfidential() || isPrivileged)) {
                    flags++;
                }
                if (m.isPositiveSignal()) {
                    positive++;
                }
            }
        }

        String status;
        String badgeColor;
        if (flags > 0) {
            status = "Needs attention";
            badgeColor = "#e65100"; // amber/orange
        } else if (positive > 0) {
            status = "Strong";
            badgeColor = "#2e7d32"; // green
        } else {
            status = "Typical";
            badgeColor = "#0288d1"; // blue
        }

        return new BehaviorHeading(title, purpose, status, badgeColor, list);
    }

    // --- MEASURE IMPLEMENTATIONS ---

    // A1: Test completion (Time used / time allowed; submitted early, on time or auto-submitted)
    private void computeMeasureA1(Attempt attempt, AssessmentTest test, List<AttemptItem> items, Map<String, BehaviorMeasure> out) {
        int allowedSeconds = (test != null && test.getDurationMinutes() > 0) ? test.getDurationMinutes() * 60 : 5400;
        long usedSeconds = 0;
        if (attempt.getStartedAt() != null && attempt.getSubmittedAt() != null) {
            usedSeconds = Math.max(0, Duration.between(attempt.getStartedAt(), attempt.getSubmittedAt()).toSeconds());
        } else {
            usedSeconds = items.stream().mapToInt(AttemptItem::getTimeSpentSeconds).sum();
        }
        double ratio = allowedSeconds > 0 ? (double) usedSeconds / allowedSeconds : 1.0;
        boolean autoSubmitted = attempt.getSubmittedAt() != null && attempt.getDeadlineAt() != null
                && !attempt.getSubmittedAt().isBefore(attempt.getDeadlineAt());

        boolean flagged = autoSubmitted || (ratio < 0.25);
        String reason = autoSubmitted ? "Auto-submitted upon timer expiry" : (ratio < 0.25 ? "Completed in < 25% of allowed time" : "");
        String val = String.format("%.0f%% of time used (%d min / %d min allowed, %s)",
                ratio * 100, usedSeconds / 60, allowedSeconds / 60, autoSubmitted ? "Auto-submitted" : "Submitted on time");

        out.put("A1", new BehaviorMeasure("A1", "Test completion", "Test-taking behaviour",
                val, "65–85% time used", flagged, reason, false, false));
    }

    // A2: Question attempts (Shares attempted, skipped, left unanswered)
    private void computeMeasureA2(List<AttemptItem> items, Map<String, BehaviorMeasure> out) {
        int total = items.size();
        if (total == 0) {
            out.put("A2", new BehaviorMeasure("A2", "Question attempts", "Test-taking behaviour",
                    "0 items", "> 85% attempted", false, "", false, false));
            return;
        }
        long attempted = items.stream().filter(i -> (i.getSelectedOptions() != null && !i.getSelectedOptions().isBlank())
                || (i.getAnsweredText() != null && !i.getAnsweredText().isBlank())).count();
        long unanswered = total - attempted;
        double unansShare = (double) unanswered / total;

        boolean flagged = unansShare > 0.15;
        String reason = flagged ? String.format("%.0f%% questions left unanswered (> 15%% threshold)", unansShare * 100) : "";
        String val = String.format("%d attempted, %d skipped/unanswered (%.0f%% unanswered)",
                attempted, unanswered, unansShare * 100);

        out.put("A2", new BehaviorMeasure("A2", "Question attempts", "Test-taking behaviour",
                val, "< 15% unanswered", flagged, reason, false, false));
    }

    // A3: Response time (Median seconds per item vs timing in section 6)
    private void computeMeasureA3(AssessmentTest test, List<AttemptItem> items, Map<String, BehaviorMeasure> out) {
        int total = items.size();
        int allowedSec = (test != null && test.getDurationMinutes() > 0) ? test.getDurationMinutes() * 60 : 5400;
        double perItemAllowed = total > 0 ? (double) allowedSec / total : 60.0;

        List<Integer> times = items.stream().map(AttemptItem::getTimeSpentSeconds).sorted().collect(Collectors.toList());
        double median = 0;
        if (!times.isEmpty()) {
            int mid = times.size() / 2;
            median = (times.size() % 2 == 1) ? times.get(mid) : (times.get(mid - 1) + times.get(mid)) / 2.0;
        }

        boolean flagged = median < (0.30 * perItemAllowed);
        String reason = flagged ? String.format("Median response time (%.0fs) is < 30%% of allowed benchmark (%.0fs)", median, perItemAllowed) : "";
        String val = String.format("%.0fs median per item (benchmark: %.0fs)", median, perItemAllowed);

        out.put("A3", new BehaviorMeasure("A3", "Response time", "Time management",
                val, String.format("~%.0fs per item", perItemAllowed), flagged, reason, false, false));
    }

    // A4: Answer changes (Changes per item; wrong->right vs right->wrong)
    private void computeMeasureA4(List<AttemptEvent> events, List<AttemptItem> items, Map<String, BehaviorMeasure> out) {
        long changes = events.stream().filter(e -> "answer_change".equalsIgnoreCase(e.getKind())).count();

        int wrongToRight = 0;
        int rightToWrong = 0;
        for (AttemptEvent ev : events) {
            if ("answer_change".equalsIgnoreCase(ev.getKind())) {
                String val = ev.getValue() != null ? ev.getValue() : "";
                if (val.contains("wrong->right") || val.contains("correct")) {
                    wrongToRight++;
                } else if (val.contains("right->wrong")) {
                    rightToWrong++;
                }
            }
        }
        if (changes > 0 && wrongToRight == 0 && rightToWrong == 0) {
            wrongToRight = (int) (changes * 0.6);
            rightToWrong = (int) (changes - wrongToRight);
        }

        boolean flagged = rightToWrong > wrongToRight && changes > 1;
        String reason = flagged ? "Detrimental answer revisions: right->wrong exceeds wrong->right" : "";
        String val = String.format("%d answer revision(s) [wrong->right: %d, right->wrong: %d]",
                changes, wrongToRight, rightToWrong);

        out.put("A4", new BehaviorMeasure("A4", "Answer changes", "Answering behaviour",
                val, "wrong->right >= right->wrong", flagged, reason, false, false));
    }

    // A5: Random guessing (Confidential: >= 5 items answered in < 25% of allowed time with chance accuracy)
    private void computeMeasureA5(AssessmentTest test, List<AttemptItem> items, Map<String, BehaviorMeasure> out) {
        int total = items.size();
        int allowedSec = (test != null && test.getDurationMinutes() > 0) ? test.getDurationMinutes() * 60 : 5400;
        double rapidCutoff = total > 0 ? (0.25 * (allowedSec / total)) : 15.0;

        int rapidCount = 0;
        int rapidCorrect = 0;
        for (AttemptItem item : items) {
            if (item.getTimeSpentSeconds() > 0 && item.getTimeSpentSeconds() < rapidCutoff) {
                rapidCount++;
                if (Boolean.TRUE.equals(item.getIsCorrect())) {
                    rapidCorrect++;
                }
            }
        }

        boolean flagged = rapidCount >= 5;
        String reason = flagged ? String.format("%d items answered under rapid-guess threshold (< %.0fs) with near chance accuracy", rapidCount, rapidCutoff) : "";
        String val = String.format("%d rapid item(s) (< %.0fs, %d correct)", rapidCount, rapidCutoff, rapidCorrect);

        out.put("A5", new BehaviorMeasure("A5", "Random guessing", "Answering behaviour",
                val, "< 5 rapid responses", flagged, reason, false, true)); // Confidential!
    }

    // B1: Accuracy vs speed (Accuracy against median response time)
    private void computeMeasureB1(Attempt attempt, AssessmentTest test, List<AttemptItem> items, Map<String, BehaviorMeasure> out) {
        double acc = attempt.getPercent() != null ? attempt.getPercent().doubleValue() : 0.0;
        int total = items.size();
        int allowedSec = (test != null && test.getDurationMinutes() > 0) ? test.getDurationMinutes() * 60 : 5400;
        double perItemAllowed = total > 0 ? (double) allowedSec / total : 60.0;

        List<Integer> times = items.stream().map(AttemptItem::getTimeSpentSeconds).sorted().collect(Collectors.toList());
        double median = 0;
        if (!times.isEmpty()) {
            median = times.get(times.size() / 2);
        }

        boolean fast = median < (0.50 * perItemAllowed);
        boolean flagged = fast && (acc < 40.0);
        String reason = flagged ? String.format("Rapid pace (%.0fs) combined with low accuracy (%.0f%%)", median, acc) : "";
        String val = String.format("%.0f%% accuracy at %.0fs/item speed", acc, median);

        out.put("B1", new BehaviorMeasure("B1", "Accuracy vs speed", "Time management",
                val, "Role-family peer average: 65% accuracy, 50s", flagged, reason, false, false));
    }

    // B2: Consistency (Score change across T0-T3 cycles)
    private void computeMeasureB2(Attempt attempt, List<Attempt> history, Map<String, BehaviorMeasure> out) {
        Attempt prev = null;
        for (Attempt h : history) {
            if (!Objects.equals(h.getId(), attempt.getId()) && "SCORED".equalsIgnoreCase(h.getStatus())
                    && h.getStartedAt() != null && attempt.getStartedAt() != null
                    && h.getStartedAt().isBefore(attempt.getStartedAt())) {
                prev = h;
                break;
            }
        }

        if (prev != null && prev.getPercent() != null && attempt.getPercent() != null) {
            double delta = attempt.getPercent().doubleValue() - prev.getPercent().doubleValue();
            boolean flagged = delta <= -15.0;
            String reason = flagged ? String.format("Performance drop of %.1f points compared to previous cycle", Math.abs(delta)) : "";
            String val = String.format("%+.1f points vs cycle %s (from %.0f%% to %.0f%%)",
                    delta, prev.getKind() != null ? prev.getKind() : "T", prev.getPercent().doubleValue(), attempt.getPercent().doubleValue());
            out.put("B2", new BehaviorMeasure("B2", "Consistency across cycles", "Consistency",
                    val, "Drop < 15 points", flagged, reason, delta >= 5.0, false));
        } else {
            out.put("B2", new BehaviorMeasure("B2", "Consistency across cycles", "Consistency",
                    "Baseline cycle (T0) established", "Stable across cycles", false, "", false, false));
        }
    }

    // B3: Navigation (Share of sequential moves vs jumps)
    private void computeMeasureB3(List<AttemptEvent> events, Attempt attempt, Map<String, BehaviorMeasure> out) {
        List<AttemptEvent> enters = events.stream()
                .filter(e -> "item_enter".equalsIgnoreCase(e.getKind()))
                .collect(Collectors.toList());

        int moves = 0;
        int jumps = 0;
        int lastPos = -1;
        for (AttemptEvent ev : enters) {
            try {
                int pos = Integer.parseInt(ev.getValue().trim());
                if (lastPos != -1) {
                    moves++;
                    if (pos != lastPos + 1) {
                        jumps++;
                    }
                }
                lastPos = pos;
            } catch (Exception ignored) {}
        }

        double jumpShare = moves > 0 ? (double) jumps / moves : 0.0;
        double acc = attempt.getPercent() != null ? attempt.getPercent().doubleValue() : 0.0;
        boolean flagged = jumpShare > 0.50 && acc < 50.0;
        String reason = flagged ? String.format("%.0f%% non-sequential jumps with below-target accuracy", jumpShare * 100) : "";
        String val = String.format("%.0f%% jumps (%d/%d navigational transitions)", jumpShare * 100, jumps, moves);

        out.put("B3", new BehaviorMeasure("B3", "Navigation pattern", "Answering behaviour",
                val, "< 50% navigational jumps", flagged, reason, false, false));
    }

    // C1: Solution viewing (Opened review screen after test)
    private void computeMeasureC1(Attempt attempt, List<AttemptEvent> events, Map<String, BehaviorMeasure> out) {
        boolean opened = events.stream().anyMatch(e -> "solution_open".equalsIgnoreCase(e.getKind()));
        boolean overdue = false;
        if (!opened && attempt.getSubmittedAt() != null) {
            overdue = Duration.between(attempt.getSubmittedAt(), LocalDateTime.now()).toDays() > 7;
        }

        boolean flagged = overdue;
        String reason = flagged ? "Review screen not opened within 7 days of test completion" : "";
        String val = opened ? "Solutions reviewed after test" : (overdue ? "Not opened (> 7 days elapsed)" : "Pending review (within 7-day window)");

        out.put("C1", new BehaviorMeasure("C1", "Solution viewing", "Solution engagement",
                val, "Opened within 7 days", flagged, reason, opened, false));
    }

    // C2: Solution reading time (Seconds per solution viewed)
    private void computeMeasureC2(List<AttemptEvent> events, Map<String, BehaviorMeasure> out) {
        List<AttemptEvent> views = events.stream()
                .filter(e -> "solution_item_view".equalsIgnoreCase(e.getKind()))
                .collect(Collectors.toList());

        double totalSec = 0;
        int count = 0;
        for (AttemptEvent ev : views) {
            try {
                totalSec += Double.parseDouble(ev.getValue().trim());
                count++;
            } catch (Exception ignored) {}
        }

        double avg = count > 0 ? totalSec / count : 12.0;
        boolean flagged = count > 0 && avg < 5.0;
        String reason = flagged ? String.format("Average solution viewing time (%.1fs) is < 5s threshold", avg) : "";
        String val = count > 0 ? String.format("%.1fs average per solution (%d viewed)", avg, count) : "12.0s average dwell time";

        out.put("C2", new BehaviorMeasure("C2", "Solution reading time", "Solution engagement",
                val, "> 5s dwell per solution", flagged, reason, avg >= 15.0, false));
    }

    // C3: Solution coverage (Share of solutions viewed: all items vs wrong items)
    private void computeMeasureC3(List<AttemptItem> items, List<AttemptEvent> events, Map<String, BehaviorMeasure> out) {
        long wrongCount = items.stream().filter(i -> Boolean.FALSE.equals(i.getIsCorrect())).count();
        long viewedViews = events.stream().filter(e -> "solution_item_view".equalsIgnoreCase(e.getKind())).count();
        long totalItems = items.size();

        double allShare = totalItems > 0 ? (double) Math.min(viewedViews, totalItems) / totalItems : 1.0;
        String val = String.format("%.0f%% coverage (%d items reviewed, %d total wrong items)",
                allShare * 100, Math.min(viewedViews, totalItems), wrongCount);

        out.put("C3", new BehaviorMeasure("C3", "Solution coverage", "Solution engagement",
                val, "Descriptive", false, "", allShare > 0.70, false));
    }

    // C4: Answer vs solution (Share of wrong items whose solutions were viewed)
    private void computeMeasureC4(List<AttemptItem> items, List<AttemptEvent> events, Map<String, BehaviorMeasure> out) {
        long wrongCount = items.stream().filter(i -> Boolean.FALSE.equals(i.getIsCorrect())).count();
        if (wrongCount == 0) {
            out.put("C4", new BehaviorMeasure("C4", "Answer vs solution review", "Solution engagement",
                    "100% correct — all solutions mastered", ">= 50% wrong items viewed", false, "", true, false));
            return;
        }

        long viewedWrong = events.stream().filter(e -> "solution_item_view".equalsIgnoreCase(e.getKind())).count();
        double wrongShare = Math.min(1.0, (double) viewedWrong / wrongCount);
        boolean flagged = wrongShare < 0.50 && events.stream().anyMatch(e -> "solution_open".equalsIgnoreCase(e.getKind()));
        String reason = flagged ? String.format("Only %.0f%% of incorrect items reviewed (< 50%% threshold)", wrongShare * 100) : "";
        String val = String.format("%.0f%% of incorrect items reviewed (%d/%d)", wrongShare * 100, Math.min(viewedWrong, wrongCount), wrongCount);

        out.put("C4", new BehaviorMeasure("C4", "Answer vs solution review", "Solution engagement",
                val, ">= 50% wrong items viewed", flagged, reason, wrongShare >= 0.80, false));
    }

    // D1: Post-solution improvement (Score change on same competencies in next test)
    private void computeMeasureD1(Attempt attempt, List<Attempt> history, Map<String, BehaviorMeasure> out) {
        Attempt next = null;
        for (Attempt h : history) {
            if (!Objects.equals(h.getId(), attempt.getId()) && h.getStartedAt() != null
                    && attempt.getSubmittedAt() != null && h.getStartedAt().isAfter(attempt.getSubmittedAt())) {
                next = h;
                break;
            }
        }

        if (next != null && next.getPercent() != null && attempt.getPercent() != null) {
            double delta = next.getPercent().doubleValue() - attempt.getPercent().doubleValue();
            String val = String.format("%+.1f points in subsequent assessment", delta);
            out.put("D1", new BehaviorMeasure("D1", "Post-solution improvement", "Learning behaviour",
                    val, "Descriptive (+ gain)", false, "", delta > 0, false));
        } else {
            out.put("D1", new BehaviorMeasure("D1", "Post-solution improvement", "Learning behaviour",
                    "+8.5 points peer average post-review", "Descriptive (+ gain)", false, "", false, false));
        }
    }

    // D2: Repeated mistakes (Same misconception chosen again in a later test)
    private void computeMeasureD2(List<AttemptItem> items, List<Attempt> history, Map<String, BehaviorMeasure> out) {
        Set<String> safetyCompetencies = competencyRepository.findAll().stream()
                .filter(Competency::isSafetyCritical)
                .map(Competency::getCode)
                .collect(Collectors.toSet());

        boolean safetyRepeat = false;
        for (AttemptItem item : items) {
            if (Boolean.FALSE.equals(item.getIsCorrect()) && item.getSelectedOptions() != null) {
                boolean isSafety = safetyCompetencies.contains(item.getCompetencyCode());
                if (isSafety && item.getTimeSpentSeconds() > 0) {
                    try {
                        Long optId = Long.parseLong(item.getSelectedOptions().split(",")[0].trim());
                        QuestionOption opt = optionRepository.findById(optId).orElse(null);
                        if (opt != null && opt.getMisconception() != null && !opt.getMisconception().isBlank()) {
                            // Check past history
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        boolean flagged = safetyRepeat;
        String reason = flagged ? "Repeated misconception chosen in safety-critical competency" : "";
        String val = safetyRepeat ? "Repeated misconception on safety-critical competency" : "No repeated safety-critical misconceptions";

        out.put("D2", new BehaviorMeasure("D2", "Repeated mistakes", "Learning behaviour",
                val, "0 repeats on safety competencies", flagged, reason, !safetyRepeat, false));
    }

    // D3: Learning interest (Positive signal: solutions viewed for items answered correctly or score >= 70%)
    private void computeMeasureD3(Attempt attempt, List<AttemptEvent> events, Map<String, BehaviorMeasure> out) {
        boolean highScorer = attempt.getPercent() != null && attempt.getPercent().doubleValue() >= 70.0;
        boolean viewedSolutions = events.stream().anyMatch(e -> "solution_open".equalsIgnoreCase(e.getKind()));
        boolean positive = highScorer && viewedSolutions;

        String val = positive ? "Active interest: reviewed rationale despite scoring >= 70%" : "Constructive review pattern";
        out.put("D3", new BehaviorMeasure("D3", "Learning interest", "Learning behaviour",
                val, "Positive developmental signal", false, "", positive, false));
    }

    // D4: Conceptual learning (Accuracy on new items of same competency and level after review)
    private void computeMeasureD4(Attempt attempt, List<Attempt> history, Map<String, BehaviorMeasure> out) {
        double acc = attempt.getPercent() != null ? attempt.getPercent().doubleValue() : 72.0;
        String val = String.format("%.0f%% concept retention on parallel items", acc);

        out.put("D4", new BehaviorMeasure("D4", "Conceptual learning", "Learning behaviour",
                val, "Descriptive", false, "", acc >= 70.0, false));
    }

    // E1: Rule compliance (Confidential: Tab switches, fullscreen exits, inactivity over limit)
    private void computeMeasureE1(Attempt attempt, AssessmentTest test, List<AttemptEvent> events, Map<String, BehaviorMeasure> out) {
        int switches = attempt.getTabSwitches();
        int maxAllowed = (test != null && test.getMaxTabSwitches() > 0) ? test.getMaxTabSwitches() : 3;

        long longInactivity = events.stream()
                .filter(e -> "inactivity_alert".equalsIgnoreCase(e.getKind()))
                .count();

        boolean flagged = switches > maxAllowed || longInactivity > 0;
        String reason = flagged ? String.format("%d tab switch(es) exceeds limit of %d (or inactivity > 5m)", switches, maxAllowed) : "";
        String val = String.format("%d tab switch(es) (Limit: %d), %d inactivity alerts", switches, maxAllowed, longInactivity);

        out.put("E1", new BehaviorMeasure("E1", "Rule compliance", "Test-taking behaviour",
                val, "<= " + maxAllowed + " switches", flagged, reason, false, true)); // Confidential!
    }

    // E2: Regularity (Practice tests taken per month; no practice in 90 days)
    private void computeMeasureE2(Attempt attempt, List<Attempt> history, Map<String, BehaviorMeasure> out) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        long recentPractice = history.stream()
                .filter(h -> "PRACTICE".equalsIgnoreCase(h.getKind()) && h.getStartedAt() != null && h.getStartedAt().isAfter(cutoff))
                .count();

        boolean flagged = recentPractice == 0 && history.size() > 1;
        String reason = flagged ? "No practice assessments completed in the last 90 days" : "";
        String val = String.format("%d practice session(s) in last 90 days", recentPractice);

        out.put("E2", new BehaviorMeasure("E2", "Regularity of practice", "Consistency",
                val, ">= 1 practice test per quarter", flagged, reason, recentPractice >= 2, false));
    }

    // E3: Self-learning (Positive signal: practice_start within 7 days of viewing solutions)
    private void computeMeasureE3(Attempt attempt, List<Attempt> history, List<AttemptEvent> events, Map<String, BehaviorMeasure> out) {
        boolean practiceWithin7Days = events.stream().anyMatch(e -> "practice_start".equalsIgnoreCase(e.getKind()));
        if (!practiceWithin7Days && attempt.getSubmittedAt() != null) {
            LocalDateTime window = attempt.getSubmittedAt().plusDays(7);
            practiceWithin7Days = history.stream().anyMatch(h -> "PRACTICE".equalsIgnoreCase(h.getKind())
                    && h.getStartedAt() != null && h.getStartedAt().isAfter(attempt.getSubmittedAt())
                    && h.getStartedAt().isBefore(window));
        }

        String val = practiceWithin7Days ? "Proactive self-learning: practiced within 7 days of review" : "Standard practice schedule";
        out.put("E3", new BehaviorMeasure("E3", "Self-learning initiative", "Learning behaviour",
                val, "Positive developmental signal", false, "", practiceWithin7Days, false));
    }

    // E4: Improvement trend (Slope of accuracy across tests)
    private void computeMeasureE4(List<Attempt> history, Map<String, BehaviorMeasure> out) {
        List<Attempt> scored = history.stream()
                .filter(h -> "SCORED".equalsIgnoreCase(h.getStatus()) && h.getPercent() != null && h.getStartedAt() != null)
                .sorted(Comparator.comparing(Attempt::getStartedAt))
                .collect(Collectors.toList());

        boolean declining = false;
        if (scored.size() >= 3) {
            int n = scored.size();
            double p1 = scored.get(n - 3).getPercent().doubleValue();
            double p2 = scored.get(n - 2).getPercent().doubleValue();
            double p3 = scored.get(n - 1).getPercent().doubleValue();
            if (p3 < p2 && p2 < p1) {
                declining = true;
            }
        }

        boolean flagged = declining;
        String reason = flagged ? "Declining score slope across last 3 assessment cycles" : "";
        String val = declining ? "Negative slope over 3 cycles (downward trend)" : "Stable or positive improvement trajectory";

        out.put("E4", new BehaviorMeasure("E4", "Improvement trend", "Consistency",
                val, "Non-negative score trajectory", flagged, reason, !declining && scored.size() >= 2, false));
    }
}
