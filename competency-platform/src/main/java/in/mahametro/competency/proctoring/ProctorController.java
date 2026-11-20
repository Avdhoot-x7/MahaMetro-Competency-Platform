package in.mahametro.competency.proctoring;

import in.mahametro.competency.access.CurrentUser;
import in.mahametro.competency.delivery.Attempt;
import in.mahametro.competency.delivery.AttemptItem;
import in.mahametro.competency.delivery.AttemptItemRepository;
import in.mahametro.competency.delivery.AttemptRepository;
import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.employees.EmployeeRepository;
import in.mahametro.competency.tests.AssessmentTest;
import in.mahametro.competency.tests.AssessmentTestRepository;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller for proctoring dashboard and reviewing flagged attempts.
 * Requires permission "test.proctor".
 */
@Controller
@RequestMapping("/proctor")
public class ProctorController {

    private final ProctoringService proctoringService;
    private final TimingAnalysisService timingService;
    private final AttemptRepository attemptRepository;
    private final AttemptItemRepository attemptItemRepository;
    private final AssessmentTestRepository testRepository;
    private final EmployeeRepository employeeRepository;
    private final CurrentUser currentUser;
    private final BehaviorAnalyticsService behaviorAnalyticsService;

    public ProctorController(ProctoringService proctoringService,
                             TimingAnalysisService timingService,
                             AttemptRepository attemptRepository,
                             AttemptItemRepository attemptItemRepository,
                             AssessmentTestRepository testRepository,
                             EmployeeRepository employeeRepository,
                             CurrentUser currentUser,
                             BehaviorAnalyticsService behaviorAnalyticsService) {
        this.proctoringService = proctoringService;
        this.timingService = timingService;
        this.attemptRepository = attemptRepository;
        this.attemptItemRepository = attemptItemRepository;
        this.testRepository = testRepository;
        this.employeeRepository = employeeRepository;
        this.currentUser = currentUser;
        this.behaviorAnalyticsService = behaviorAnalyticsService;
    }

    @GetMapping("/flags")
    public String flaggedAttempts(Principal principal, Model model) {
        currentUser.requireAny(principal, "test.proctor");

        List<Attempt> flagged = proctoringService.getFlaggedAttempts();
        List<Attempt> allRecent = proctoringService.getAllRecentSubmissions();

        Map<Long, Employee> candidateMap = new HashMap<>();
        Map<Long, AssessmentTest> testMap = new HashMap<>();

        for (Attempt a : allRecent) {
            if (!candidateMap.containsKey(a.getEmployeeId())) {
                employeeRepository.findById(a.getEmployeeId()).ifPresent(e -> candidateMap.put(e.getId(), e));
            }
            if (!testMap.containsKey(a.getTestId())) {
                testRepository.findById(a.getTestId()).ifPresent(t -> testMap.put(t.getId(), t));
            }
        }

        model.addAttribute("flagged", flagged);
        model.addAttribute("allRecent", allRecent);
        model.addAttribute("candidates", candidateMap);
        model.addAttribute("tests", testMap);
        return "proctor/flags";
    }

    @GetMapping("/attempts/{id}")
    public String reviewAttempt(@PathVariable("id") Long id, Principal principal, Model model) {
        currentUser.requireAny(principal, "test.proctor");

        Attempt attempt = attemptRepository.findById(id)
                .orElseThrow(() -> new AccessDeniedException("Attempt not found: " + id));
        Employee candidate = employeeRepository.findById(attempt.getEmployeeId()).orElse(null);
        AssessmentTest test = testRepository.findById(attempt.getTestId()).orElse(null);
        List<AttemptItem> items = attemptItemRepository.findByAttemptIdOrderByPositionAsc(id);
        List<ProctorEvent> events = proctoringService.getEventsForAttempt(id);
        TimingAnalysisService.TimingSummary timing = timingService.analyse(attempt, items);
        BehaviorAnalyticsService.BehaviorReport behaviorReport = behaviorAnalyticsService.generateReport(attempt, true);

        model.addAttribute("attempt", attempt);
        model.addAttribute("candidate", candidate);
        model.addAttribute("test", test);
        model.addAttribute("items", items);
        model.addAttribute("events", events);
        model.addAttribute("timing", timing);
        model.addAttribute("behaviorReport", behaviorReport);
        return "proctor/attempt";
    }

    @PostMapping("/attempts/{id}/decision")
    public String decide(
            @PathVariable("id") Long id,
            @RequestParam("decision") String decision,
            @RequestParam(name = "note", defaultValue = "") String note,
            Principal principal,
            RedirectAttributes redirect) {
        Employee proctor = currentUser.requireAny(principal, "test.proctor");

        if ("accept".equalsIgnoreCase(decision)) {
            proctoringService.acceptAttempt(id, proctor, note);
            redirect.addFlashAttribute("message", "Attempt #" + id + " has been accepted and scores published.");
        } else if ("void".equalsIgnoreCase(decision)) {
            proctoringService.voidAttempt(id, proctor, note);
            redirect.addFlashAttribute("message", "Attempt #" + id + " has been declared VOID.");
        } else {
            redirect.addFlashAttribute("error", "Unknown decision: " + decision);
        }

        return "redirect:/proctor/flags";
    }
}
