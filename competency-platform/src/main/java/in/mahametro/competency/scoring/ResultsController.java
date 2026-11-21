package in.mahametro.competency.scoring;

import in.mahametro.competency.access.AccessService;
import in.mahametro.competency.access.CurrentUser;
import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.employees.EmployeeRepository;
import in.mahametro.competency.development.DevelopmentPlan;
import in.mahametro.competency.development.DevelopmentPlanItem;
import in.mahametro.competency.development.Intervention;
import in.mahametro.competency.development.InterventionRepository;
import in.mahametro.competency.development.PrescriptionService;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Result pages: my own profile, and the staff a supervisor, HoD, management or the Cell may see. */
@Controller
public class ResultsController {

    private final EmployeeRepository employees;
    private final ProfileService profiles;
    private final CurrentUser currentUser;
    private final AccessService access;
    private final PrescriptionService prescriptionService;
    private final InterventionRepository interventionRepository;
    private final in.mahametro.competency.reporting.ReportingService reportingService;
    private final in.mahametro.competency.employees.RoleFamilyRepository roleFamilies;

    public ResultsController(EmployeeRepository employees,
                             ProfileService profiles,
                             CurrentUser currentUser,
                             AccessService access,
                             PrescriptionService prescriptionService,
                             InterventionRepository interventionRepository,
                             in.mahametro.competency.reporting.ReportingService reportingService,
                             in.mahametro.competency.employees.RoleFamilyRepository roleFamilies) {
        this.employees = employees;
        this.profiles = profiles;
        this.currentUser = currentUser;
        this.access = access;
        this.prescriptionService = prescriptionService;
        this.interventionRepository = interventionRepository;
        this.reportingService = reportingService;
        this.roleFamilies = roleFamilies;
    }

    @GetMapping("/me")
    public String mine(Principal principal, Model model) {
        Employee me = currentUser.get(principal);
        model.addAttribute("employee", me);
        model.addAttribute("profile", profiles.build(me));
        model.addAttribute("own", true);
        attachPlan(me, me, model);
        return "results/profile";
    }

    @GetMapping("/results")
    public String list(@org.springframework.web.bind.annotation.ModelAttribute in.mahametro.competency.reporting.StaffFilter filter,
                       @org.springframework.web.bind.annotation.RequestParam(name = "page", defaultValue = "0") int page,
                       @org.springframework.web.bind.annotation.RequestParam(name = "size", defaultValue = "50") int size,
                       Principal principal, Model model) {
        Employee me = currentUser.get(principal);
        if (!access.hasAny(me.getId(), "team.view", "department.view", "org.view", "test.manage")) {
            return "redirect:/me";
        }
        org.springframework.data.domain.Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(1, size));
        org.springframework.data.domain.Page<in.mahametro.competency.reporting.StaffSummaryRow> result =
                reportingService.getPagedStaffResults(me, filter, pageable);
        Map<String, Integer> counts = reportingService.getHeadlineCounts(me, filter);

        model.addAttribute("pageResult", result);
        model.addAttribute("staffRows", result.getContent());
        model.addAttribute("filter", filter);
        model.addAttribute("red", counts.get("red"));
        model.addAttribute("amber", counts.get("amber"));
        model.addAttribute("green", counts.get("green"));
        model.addAttribute("notAssessed", counts.get("notAssessed"));
        model.addAttribute("total", counts.get("total"));
        model.addAttribute("departments", employees.findDistinctDepartments());
        model.addAttribute("families", roleFamilies.findAll(Sort.by("code")));
        return "results/list";
    }

    @GetMapping("/results/{id}")
    public String one(@PathVariable("id") Long id, Principal principal, Model model) {
        Employee me = currentUser.get(principal);
        Employee target = employees.findById(id).orElseThrow(() -> new AccessDeniedException("No such employee"));
        if (!canView(me, target)) {
            throw new AccessDeniedException("You may not see this person's results");
        }
        model.addAttribute("employee", target);
        model.addAttribute("profile", profiles.build(target));
        model.addAttribute("own", false);
        attachPlan(me, target, model);
        return "results/profile";
    }

    private void attachPlan(Employee viewer, Employee target, Model model) {
        prescriptionService.getLatestPlan(target.getId()).ifPresent(plan -> {
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
            boolean canConfirm = DevelopmentPlan.STATUS_PROPOSED.equalsIgnoreCase(plan.getStatus()) &&
                    (viewer.getId().equals(target.getId()) ||
                     viewer.getEmpId().equals(target.getReportingOfficer()) ||
                     access.hasAny(viewer.getId(), "test.manage"));
            model.addAttribute("canConfirmPlan", canConfirm);
        });
    }

    /** The person themself, their reporting officer, the HoD of their department, management and the Cell. */
    private boolean canView(Employee viewer, Employee target) {
        if (viewer.getId().equals(target.getId()) || access.hasAny(viewer.getId(), "org.view")) {
            return true;
        }
        if (access.hasAny(viewer.getId(), "team.view") && viewer.getEmpId().equals(target.getReportingOfficer())) {
            return true;
        }
        return access.can(viewer.getId(), "department.view", "DEPARTMENT", target.getDepartment());
    }
}
