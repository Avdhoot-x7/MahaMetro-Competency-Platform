package in.mahametro.competency.auth;

import in.mahametro.competency.access.AccessService;
import in.mahametro.competency.delivery.Attempt;
import in.mahametro.competency.delivery.AttemptRepository;
import in.mahametro.competency.delivery.PracticeController;
import in.mahametro.competency.development.DevelopmentPlan;
import in.mahametro.competency.development.DevelopmentPlanItem;
import in.mahametro.competency.development.Intervention;
import in.mahametro.competency.development.InterventionRepository;
import in.mahametro.competency.development.PrescriptionService;
import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.employees.EmployeeRepository;
import in.mahametro.competency.reporting.ProfileSummaryRepository;
import in.mahametro.competency.scoring.ProfileService;
import in.mahametro.competency.tests.AssessmentTest;
import in.mahametro.competency.tests.TestService;
import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for login and the candidate assessment hub.
 * Loads practice tests, scheduled official tests with attempt statuses (red dots for pending),
 * prominent pending alert notification, personal performance scorecard, and weak point recommendations.
 */
@Controller
public class AuthController {

    public static class TestStatusInfo {
        private final String statusType; // "COMPLETED", "IN_PROGRESS", "NOT_ATTEMPTED"
        private final String displayBadge;
        private final boolean notAttempted;
        private final Long attemptId;
        private final String dateStr;

        public TestStatusInfo(String statusType, String displayBadge, boolean notAttempted, Long attemptId, String dateStr) {
            this.statusType = statusType;
            this.displayBadge = displayBadge;
            this.notAttempted = notAttempted;
            this.attemptId = attemptId;
            this.dateStr = dateStr;
        }

        public String getStatusType() { return statusType; }
        public String getDisplayBadge() { return displayBadge; }
        public boolean isNotAttempted() { return notAttempted; }
        public Long getAttemptId() { return attemptId; }
        public String getDateStr() { return dateStr; }
    }

    private final EmployeeRepository employees;
    private final AccessService accessService;
    private final ProfileSummaryRepository profileSummaries;
    private final ProfileService profileService;
    private final TestService testService;
    private final PrescriptionService prescriptionService;
    private final InterventionRepository interventionRepository;
    private final AttemptRepository attemptRepository;
    private final PracticeController practiceController;

    public AuthController(EmployeeRepository employees,
                          AccessService accessService,
                          ProfileSummaryRepository profileSummaries,
                          ProfileService profileService,
                          TestService testService,
                          PrescriptionService prescriptionService,
                          InterventionRepository interventionRepository,
                          AttemptRepository attemptRepository,
                          PracticeController practiceController) {
        this.employees = employees;
        this.accessService = accessService;
        this.profileSummaries = profileSummaries;
        this.profileService = profileService;
        this.testService = testService;
        this.prescriptionService = prescriptionService;
        this.interventionRepository = interventionRepository;
        this.attemptRepository = attemptRepository;
        this.practiceController = practiceController;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String home(Principal principal, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        Employee me = employees.findByEmpId(principal.getName()).orElse(null);
        if (me == null) {
            return "redirect:/login";
        }
        model.addAttribute("employee", me);

        boolean isExecutive = accessService.hasAny(me.getId(),
                "test.manage", "org.view", "team.view", "question.create", "proctor.view");
        model.addAttribute("isExecutive", isExecutive);

        // 1. Personal Performance Scorecard
        profileSummaries.findById(me.getId()).ifPresent(summary -> model.addAttribute("summary", summary));
        try {
            model.addAttribute("profile", profileService.build(me));
        } catch (Exception ignored) {
        }

        // 2. Practice Topics from Formative Practice Engine
        model.addAttribute("practiceSections", practiceController.getSections());

        // 3. Tests: Practice Tests & Scheduled Attempt Tests with Attempt Statuses
        List<AssessmentTest> available = testService.findAvailableTests(me);
        List<AssessmentTest> practiceTests = available.stream()
                .filter(AssessmentTest::isPractice)
                .collect(Collectors.toList());
        List<AssessmentTest> attemptTests = available.stream()
                .filter(t -> !t.isPractice())
                .collect(Collectors.toList());
        model.addAttribute("practiceTests", practiceTests);
        model.addAttribute("attemptTests", attemptTests);

        // Load attempts for this candidate to check status of each official test
        List<Attempt> candidateAttempts = attemptRepository.findByEmployeeIdOrderByStartedAtDesc(me.getId());
        Map<Long, List<Attempt>> attemptsByTest = candidateAttempts.stream()
                .collect(Collectors.groupingBy(Attempt::getTestId));

        Map<Long, TestStatusInfo> testStatusMap = new HashMap<>();
        int pendingOfficialCount = 0;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (AssessmentTest t : attemptTests) {
            List<Attempt> list = attemptsByTest.getOrDefault(t.getId(), Collections.emptyList());
            if (list.isEmpty()) {
                testStatusMap.put(t.getId(), new TestStatusInfo("NOT_ATTEMPTED", "🔴 Not Attempted", true, null, ""));
                pendingOfficialCount++;
            } else {
                Attempt latest = list.get(0);
                if (Attempt.STATUS_SCORED.equalsIgnoreCase(latest.getStatus()) ||
                    Attempt.STATUS_SUBMITTED.equalsIgnoreCase(latest.getStatus()) ||
                    Attempt.STATUS_FLAGGED.equalsIgnoreCase(latest.getStatus())) {
                    String scoreText = latest.getPercent() != null ? String.format(Locale.US, "%.1f%%", latest.getPercent()) : "Scored";
                    String dateText = latest.getSubmittedAt() != null ? latest.getSubmittedAt().format(dtf) :
                            (latest.getStartedAt() != null ? latest.getStartedAt().format(dtf) : "");
                    testStatusMap.put(t.getId(), new TestStatusInfo("COMPLETED", "🟢 Completed (Score: " + scoreText + ")", false, latest.getId(), dateText));
                } else if (latest.isInProgress()) {
                    testStatusMap.put(t.getId(), new TestStatusInfo("IN_PROGRESS", "🟡 In Progress (Resume)", false, latest.getId(), ""));
                    pendingOfficialCount++;
                } else {
                    testStatusMap.put(t.getId(), new TestStatusInfo("NOT_ATTEMPTED", "🔴 Not Attempted", true, null, ""));
                    pendingOfficialCount++;
                }
            }
        }
        model.addAttribute("testStatusMap", testStatusMap);
        model.addAttribute("pendingOfficialCount", pendingOfficialCount);

        // 4. Weak Point Recommendations (IDP & Interventions)
        prescriptionService.getLatestPlan(me.getId()).ifPresent(plan -> {
            model.addAttribute("plan", plan);
            List<DevelopmentPlanItem> items = prescriptionService.getItems(plan.getId());
            model.addAttribute("planItems", items);
            List<Long> ivIds = items.stream()
                    .map(DevelopmentPlanItem::getInterventionId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            Map<Long, Intervention> ivMap = interventionRepository.findAllById(ivIds).stream()
                    .collect(Collectors.toMap(Intervention::getId, i -> i));
            model.addAttribute("interventions", ivMap);
        });

        return "home";
    }
}
