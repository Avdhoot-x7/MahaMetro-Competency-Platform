package in.mahametro.competency.delivery;

import in.mahametro.competency.access.CurrentUser;
import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.questions.Question;
import in.mahametro.competency.questions.QuestionOption;
import in.mahametro.competency.questions.QuestionOptionRepository;
import in.mahametro.competency.questions.QuestionRepository;
import in.mahametro.competency.tests.AssessmentTest;
import in.mahametro.competency.tests.AssessmentTestRepository;
import in.mahametro.competency.tests.TestService;
import java.security.Principal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Test player endpoints: available tests, launching attempts, timer player, autosave, and submission. */
@Controller
@RequestMapping("/delivery")
public class DeliveryController {

    private final AssessmentTestRepository testRepository;
    private final TestService testService;
    private final DeliveryService deliveryService;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository optionRepository;
    private final in.mahametro.competency.proctoring.ProctoringService proctoringService;
    private final in.mahametro.competency.proctoring.BehaviorAnalyticsService behaviorAnalyticsService;
    private final in.mahametro.competency.access.AccessService accessService;
    private final CurrentUser currentUser;

    public DeliveryController(AssessmentTestRepository testRepository, TestService testService,
            DeliveryService deliveryService, QuestionRepository questionRepository,
            QuestionOptionRepository optionRepository,
            in.mahametro.competency.proctoring.ProctoringService proctoringService,
            in.mahametro.competency.proctoring.BehaviorAnalyticsService behaviorAnalyticsService,
            in.mahametro.competency.access.AccessService accessService,
            CurrentUser currentUser) {
        this.testRepository = testRepository;
        this.testService = testService;
        this.deliveryService = deliveryService;
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.proctoringService = proctoringService;
        this.behaviorAnalyticsService = behaviorAnalyticsService;
        this.accessService = accessService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public String availableTests(Principal principal, Model model) {
        Employee me = currentUser.get(principal);
        List<AssessmentTest> available = testService.findAvailableTests(me);
        model.addAttribute("employee", me);
        model.addAttribute("tests", available);
        return "delivery/tests";
    }

    @PostMapping("/tests/{id}/start")
    public String startTest(@PathVariable("id") Long id, Principal principal, RedirectAttributes redirect) {
        Employee me = currentUser.get(principal);
        try {
            Attempt attempt = deliveryService.startOrResumeAttempt(id, me);
            return "redirect:/delivery/attempts/" + attempt.getId();
        } catch (Exception e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/delivery";
        }
    }

    @GetMapping("/attempts/{id}")
    public String player(@PathVariable("id") Long id,
            @RequestParam(name = "pos", defaultValue = "1") int pos,
            Principal principal, Model model) {
        Employee me = currentUser.get(principal);
        Attempt attempt = deliveryService.findById(id);
        if (attempt == null || !attempt.getEmployeeId().equals(me.getId())) {
            throw new AccessDeniedException("Unauthorized access to this test attempt.");
        }

        if (!attempt.isInProgress()) {
            return "redirect:/delivery/attempts/" + id + "/summary";
        }

        if (attempt.isExpired()) {
            deliveryService.submitAttempt(id, me);
            return "redirect:/delivery/attempts/" + id + "/summary";
        }

        AssessmentTest test = testRepository.findById(attempt.getTestId())
                .orElseThrow(() -> new IllegalStateException("Test not found"));

        List<AttemptItem> items = deliveryService.getItems(id);
        if (items.isEmpty()) {
            throw new IllegalStateException("Attempt has no questions assembled.");
        }

        int targetPos = Math.max(1, Math.min(pos, items.size()));
        AttemptItem currentItem = items.get(targetPos - 1);

        Question q = questionRepository.findById(currentItem.getQuestionId())
                .orElseThrow(() -> new IllegalStateException("Question not found"));

        // Order options according to shuffled IDs
        List<QuestionOption> allOptions = optionRepository.findByQuestionIdOrderByOrdAsc(q.getId());
        List<QuestionOption> orderedOptions = new ArrayList<>();
        if (currentItem.getShuffledOptionIds() != null && !currentItem.getShuffledOptionIds().isBlank()) {
            Map<Long, QuestionOption> optMap = allOptions.stream()
                    .collect(Collectors.toMap(QuestionOption::getId, o -> o));
            for (String sId : currentItem.getShuffledOptionIds().split(",")) {
                try {
                    Long oId = Long.parseLong(sId.trim());
                    if (optMap.containsKey(oId)) {
                        orderedOptions.add(optMap.get(oId));
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        if (orderedOptions.isEmpty()) {
            orderedOptions = allOptions;
        }

        PlayerItemView currentView = new PlayerItemView(currentItem, q, orderedOptions);

        long remainingSeconds = Math.max(0, Duration.between(LocalDateTime.now(), attempt.getDeadlineAt()).toSeconds());

        // Parse selected options into set for template checking
        Set<String> selectedSet = Arrays.stream(currentItem.getSelectedOptions().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        model.addAttribute("attempt", attempt);
        model.addAttribute("test", test);
        model.addAttribute("items", items);
        model.addAttribute("currentItem", currentItem);
        model.addAttribute("currentView", currentView);
        model.addAttribute("currentPos", targetPos);
        model.addAttribute("totalItems", items.size());
        model.addAttribute("remainingSeconds", remainingSeconds);
        model.addAttribute("selectedSet", selectedSet);
        return "delivery/player";
    }

    @PostMapping({"/attempts/{id}/answer", "/attempts/{id}/autosave"})
    @ResponseBody
    public ResponseEntity<?> autosaveAnswer(
            @PathVariable("id") Long id,
            @RequestParam("position") int position,
            @RequestParam(name = "selectedOptions", defaultValue = "") String selectedOptions,
            @RequestParam(name = "answeredText", defaultValue = "") String answeredText,
            @RequestParam(name = "timeSpentSeconds", defaultValue = "0") int timeSpentSeconds,
            Principal principal) {
        Employee me = currentUser.get(principal);
        deliveryService.recordAnswer(id, me, position, selectedOptions, answeredText, timeSpentSeconds);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/attempts/{id}/event")
    @ResponseBody
    public ResponseEntity<?> recordEvent(
            @PathVariable("id") Long id,
            @RequestParam("kind") String kind,
            @RequestParam(name = "itemId", required = false) Long itemId,
            @RequestParam(name = "detail", defaultValue = "") String detail,
            Principal principal) {
        Employee me = currentUser.get(principal);
        int tabSwitches = proctoringService.recordEvent(id, me, kind, detail, null);
        deliveryService.recordAttemptEvent(id, itemId, kind, detail);
        return ResponseEntity.ok(Map.of("status", "ok", "tabSwitches", tabSwitches));
    }

    @PostMapping("/attempts/{id}/snapshot")
    @ResponseBody
    public ResponseEntity<?> uploadSnapshot(
            @PathVariable("id") Long id,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            Principal principal) throws java.io.IOException {
        Employee me = currentUser.get(principal);
        Long mediaId = proctoringService.saveSnapshot(id, me, file);
        return ResponseEntity.ok(Map.of("status", "ok", "mediaId", mediaId));
    }

    @PostMapping("/attempts/{id}/submit")
    public String submit(@PathVariable("id") Long id, Principal principal) {
        Employee me = currentUser.get(principal);
        deliveryService.submitAttempt(id, me);
        return "redirect:/delivery/attempts/" + id + "/summary";
    }

    @GetMapping("/attempts/{id}/summary")
    public String summary(@PathVariable("id") Long id, Principal principal, Model model) {
        Employee me = currentUser.get(principal);
        Attempt attempt = deliveryService.findById(id);
        boolean isPrivileged = accessService.hasAny(me.getId(), "test.proctor") || accessService.hasAny(me.getId(), "test.manage");
        if (attempt == null || (!attempt.getEmployeeId().equals(me.getId()) && !isPrivileged)) {
            throw new AccessDeniedException("Unauthorized access to this test attempt.");
        }

        AssessmentTest test = testRepository.findById(attempt.getTestId()).orElse(null);
        in.mahametro.competency.proctoring.BehaviorAnalyticsService.BehaviorReport report =
                behaviorAnalyticsService.generateReport(attempt, isPrivileged);

        model.addAttribute("attempt", attempt);
        model.addAttribute("test", test);
        model.addAttribute("employee", me);
        model.addAttribute("behaviorReport", report);
        return "delivery/summary";
    }

    @GetMapping("/attempts/{id}/review")
    public String reviewSolutions(@PathVariable("id") Long id, Principal principal, Model model) {
        Employee me = currentUser.get(principal);
        Attempt attempt = deliveryService.findById(id);
        boolean isPrivileged = accessService.hasAny(me.getId(), "test.proctor") || accessService.hasAny(me.getId(), "test.manage");
        if (attempt == null || (!attempt.getEmployeeId().equals(me.getId()) && !isPrivileged)) {
            throw new AccessDeniedException("Unauthorized access to this test attempt.");
        }

        AssessmentTest test = testRepository.findById(attempt.getTestId()).orElse(null);
        List<AttemptItem> items = deliveryService.getItems(id);

        deliveryService.recordAttemptEvent(id, null, "solution_open", "Candidate opened solution review screen");

        List<Map<String, Object>> reviewItems = new ArrayList<>();
        for (AttemptItem item : items) {
            Question q = questionRepository.findById(item.getQuestionId()).orElse(null);
            List<QuestionOption> opts = q != null ? optionRepository.findByQuestionIdOrderByOrdAsc(q.getId()) : Collections.emptyList();
            Map<String, Object> map = new HashMap<>();
            map.put("item", item);
            if (q != null) map.put("question", q);
            map.put("options", opts);
            reviewItems.add(map);
            deliveryService.recordAttemptEvent(id, item.getQuestionId(), "solution_item_view", "10");
        }

        model.addAttribute("attempt", attempt);
        model.addAttribute("test", test);
        model.addAttribute("employee", me);
        model.addAttribute("reviewItems", reviewItems);
        return "delivery/review";
    }
}
