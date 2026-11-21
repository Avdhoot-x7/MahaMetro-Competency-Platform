package in.mahametro.competency.auth;

import in.mahametro.competency.access.AccessService;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for login and the landing page.
 * For employees, loads the candidate learning & assessment hub:
 * practice tests, attempt tests, personal performance scorecard, and weak point recommendations.
 */
@Controller
public class AuthController {

    private final EmployeeRepository employees;
    private final AccessService accessService;
    private final ProfileSummaryRepository profileSummaries;
    private final ProfileService profileService;
    private final TestService testService;
    private final PrescriptionService prescriptionService;
    private final InterventionRepository interventionRepository;

    public AuthController(EmployeeRepository employees,
                          AccessService accessService,
                          ProfileSummaryRepository profileSummaries,
                          ProfileService profileService,
                          TestService testService,
                          PrescriptionService prescriptionService,
                          InterventionRepository interventionRepository) {
        this.employees = employees;
        this.accessService = accessService;
        this.profileSummaries = profileSummaries;
        this.profileService = profileService;
        this.testService = testService;
        this.prescriptionService = prescriptionService;
        this.interventionRepository = interventionRepository;
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

        // 2. Tests: Practice Tests & Scheduled Attempt Tests
        List<AssessmentTest> available = testService.findAvailableTests(me);
        List<AssessmentTest> practiceTests = available.stream()
                .filter(AssessmentTest::isPractice)
                .collect(Collectors.toList());
        List<AssessmentTest> attemptTests = available.stream()
                .filter(t -> !t.isPractice())
                .collect(Collectors.toList());
        model.addAttribute("practiceTests", practiceTests);
        model.addAttribute("attemptTests", attemptTests);

        // 3. Weak Point Recommendations (IDP & Interventions)
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
