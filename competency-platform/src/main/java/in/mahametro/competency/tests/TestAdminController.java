package in.mahametro.competency.tests;

import in.mahametro.competency.access.CurrentUser;
import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.employees.RoleFamilyRepository;
import java.security.Principal;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Assessment Cell screens for managing, scheduling, publishing and closing tests. */
@Controller
@RequestMapping("/admin/tests")
public class TestAdminController {

    private final AssessmentTestRepository testRepository;
    private final TestService testService;
    private final RoleFamilyRepository roleFamilies;
    private final CurrentUser currentUser;

    public TestAdminController(AssessmentTestRepository testRepository, TestService testService,
            RoleFamilyRepository roleFamilies, CurrentUser currentUser) {
        this.testRepository = testRepository;
        this.testService = testService;
        this.roleFamilies = roleFamilies;
        this.currentUser = currentUser;
    }

    @GetMapping
    public String list(@RequestParam(name = "status", defaultValue = "") String status,
            @RequestParam(name = "page", defaultValue = "0") int page, Principal principal, Model model) {
        currentUser.requireAny(principal, "test.manage");
        Pageable pageable = PageRequest.of(Math.max(page, 0), 25, Sort.by(Sort.Direction.DESC, "id"));
        Page<AssessmentTest> result = status.isBlank()
                ? testRepository.findAll(pageable)
                : testRepository.findByStatus(status.toUpperCase(), pageable);

        model.addAttribute("result", result);
        model.addAttribute("status", status);
        model.addAttribute("draftCount", testRepository.countByStatus(AssessmentTest.STATUS_DRAFT));
        model.addAttribute("publishedCount", testRepository.countByStatus(AssessmentTest.STATUS_PUBLISHED));
        model.addAttribute("closedCount", testRepository.countByStatus(AssessmentTest.STATUS_CLOSED));
        return "admin/tests/list";
    }

    @GetMapping("/new")
    public String createForm(Principal principal, Model model) {
        currentUser.requireAny(principal, "test.manage");
        model.addAttribute("roleFamilies", roleFamilies.findAll(Sort.by("nameEn")));
        return "admin/tests/new";
    }

    @PostMapping("/new")
    public String create(
            @RequestParam("code") String code,
            @RequestParam("titleEn") String titleEn,
            @RequestParam(name = "titleMr", defaultValue = "") String titleMr,
            @RequestParam(name = "kind", defaultValue = "T0") String kind,
            @RequestParam(name = "durationMinutes", defaultValue = "90") int durationMinutes,
            @RequestParam(name = "maxAttempts", defaultValue = "1") int maxAttempts,
            @RequestParam(name = "itemsPerCompetency", defaultValue = "3") int itemsPerCompetency,
            @RequestParam(name = "maxItems", defaultValue = "80") int maxItems,
            @RequestParam(name = "targetType", defaultValue = "ALL") String targetType,
            @RequestParam(name = "targetValue", defaultValue = "") String targetValue,
            @RequestParam(name = "startAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @RequestParam(name = "endAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt,
            @RequestParam(name = "shuffleQuestions", defaultValue = "true") boolean shuffleQuestions,
            @RequestParam(name = "shuffleOptions", defaultValue = "true") boolean shuffleOptions,
            @RequestParam(name = "allowBack", defaultValue = "true") boolean allowBack,
            Principal principal,
            RedirectAttributes redirect) {
        Employee actor = currentUser.requireAny(principal, "test.manage");
        try {
            AssessmentTest test = new AssessmentTest(code, titleEn, kind, durationMinutes);
            test.setTitleMr(titleMr);
            test.setMaxAttempts(maxAttempts);
            test.setItemsPerCompetency(itemsPerCompetency);
            test.setMaxItems(maxItems);
            test.setTargetType(targetType);
            test.setTargetValue(targetValue);
            test.setStartAt(startAt);
            test.setEndAt(endAt);
            test.setShuffleQuestions(shuffleQuestions);
            test.setShuffleOptions(shuffleOptions);
            test.setAllowBack(allowBack);

            AssessmentTest created = testService.createTest(test, actor);
            redirect.addFlashAttribute("message", "Test " + created.getCode() + " created as draft.");
            return "redirect:/admin/tests/" + created.getId();
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/tests/new";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Long id, Principal principal, Model model) {
        currentUser.requireAny(principal, "test.manage");
        AssessmentTest test = testRepository.findById(id)
                .orElseThrow(() -> new AccessDeniedException("No such test: " + id));
        model.addAttribute("test", test);
        return "admin/tests/view";
    }

    @PostMapping("/{id}/publish")
    public String publish(@PathVariable("id") Long id, Principal principal, RedirectAttributes redirect) {
        Employee actor = currentUser.requireAny(principal, "test.manage");
        testService.publishTest(id, actor);
        redirect.addFlashAttribute("message", "Test successfully published. Candidates can now see and take this test.");
        return "redirect:/admin/tests/" + id;
    }

    @PostMapping("/{id}/close")
    public String close(@PathVariable("id") Long id, Principal principal, RedirectAttributes redirect) {
        Employee actor = currentUser.requireAny(principal, "test.manage");
        testService.closeTest(id, actor);
        redirect.addFlashAttribute("message", "Test closed. No further attempts can be started.");
        return "redirect:/admin/tests/" + id;
    }
}
