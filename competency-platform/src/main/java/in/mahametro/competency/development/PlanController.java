package in.mahametro.competency.development;

import in.mahametro.competency.access.AccessService;
import in.mahametro.competency.access.CurrentUser;
import in.mahametro.competency.core.AuditService;
import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.employees.EmployeeRepository;
import in.mahametro.competency.framework.Competency;
import in.mahametro.competency.framework.CompetencyRepository;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller for Individual Development Plans (IDP) and the training catalogue.
 */
@Controller
public class PlanController {

    private final PrescriptionService prescriptionService;
    private final DevelopmentPlanRepository planRepository;
    private final DevelopmentPlanItemRepository itemRepository;
    private final InterventionRepository interventionRepository;
    private final EmployeeRepository employeeRepository;
    private final CompetencyRepository competencyRepository;
    private final CurrentUser currentUser;
    private final AccessService accessService;
    private final AuditService auditService;

    public PlanController(PrescriptionService prescriptionService,
                          DevelopmentPlanRepository planRepository,
                          DevelopmentPlanItemRepository itemRepository,
                          InterventionRepository interventionRepository,
                          EmployeeRepository employeeRepository,
                          CompetencyRepository competencyRepository,
                          CurrentUser currentUser,
                          AccessService accessService,
                          AuditService auditService) {
        this.prescriptionService = prescriptionService;
        this.planRepository = planRepository;
        this.itemRepository = itemRepository;
        this.interventionRepository = interventionRepository;
        this.employeeRepository = employeeRepository;
        this.competencyRepository = competencyRepository;
        this.currentUser = currentUser;
        this.accessService = accessService;
        this.auditService = auditService;
    }

    @GetMapping("/plans")
    public String listPlans(@RequestParam(value = "status", required = false) String statusFilter,
                            Principal principal,
                            Model model) {
        Employee me = currentUser.get(principal);

        List<DevelopmentPlan> allPlans;
        if (statusFilter != null && !statusFilter.isBlank() && !"all".equalsIgnoreCase(statusFilter)) {
            allPlans = planRepository.findByStatusOrderByCreatedAtDesc(statusFilter.toLowerCase());
        } else {
            allPlans = planRepository.findAll();
            allPlans.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        }

        // Filter plans visible to current user
        List<DevelopmentPlan> visible = new ArrayList<>();
        Map<Long, Employee> employeeMap = new HashMap<>();
        Map<Long, Integer> totalItemMap = new HashMap<>();
        Map<Long, Integer> safetyGateMap = new HashMap<>();

        for (DevelopmentPlan p : allPlans) {
            Employee emp = employeeRepository.findById(p.getEmployeeId()).orElse(null);
            if (emp == null) {
                continue;
            }
            if (canViewPlan(me, emp)) {
                visible.add(p);
                employeeMap.put(p.getId(), emp);
                List<DevelopmentPlanItem> items = itemRepository.findByPlanIdOrderByPriorityAscGapDescCompetencyCodeAsc(p.getId());
                totalItemMap.put(p.getId(), items.size());
                long safetyCount = items.stream().filter(it -> "safety".equalsIgnoreCase(it.getGapType())).count();
                safetyGateMap.put(p.getId(), (int) safetyCount);
            }
        }

        model.addAttribute("plans", visible);
        model.addAttribute("employees", employeeMap);
        model.addAttribute("totalItemMap", totalItemMap);
        model.addAttribute("safetyGateMap", safetyGateMap);
        model.addAttribute("selectedStatus", statusFilter != null ? statusFilter : "all");
        return "plans/list";
    }

    @GetMapping("/plans/{id}")
    public String viewPlan(@PathVariable("id") Long id, Principal principal, Model model) {
        Employee me = currentUser.get(principal);
        DevelopmentPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Development plan not found: " + id));

        Employee target = employeeRepository.findById(plan.getEmployeeId())
                .orElseThrow(() -> new IllegalArgumentException("Employee not found for plan: " + id));

        if (!canViewPlan(me, target)) {
            throw new AccessDeniedException("You do not have permission to view this development plan.");
        }

        List<DevelopmentPlanItem> items = prescriptionService.getItems(plan.getId());

        // Load competency metadata
        List<String> compCodes = items.stream().map(DevelopmentPlanItem::getCompetencyCode).distinct().toList();
        Map<String, Competency> compMap = competencyRepository.findAllById(compCodes).stream()
                .collect(Collectors.toMap(Competency::getCode, c -> c));

        // Load intervention metadata
        List<Long> ivIds = items.stream()
                .map(DevelopmentPlanItem::getInterventionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Intervention> ivMap = interventionRepository.findAllById(ivIds).stream()
                .collect(Collectors.toMap(Intervention::getId, iv -> iv));

        boolean canConfirm = DevelopmentPlan.STATUS_PROPOSED.equalsIgnoreCase(plan.getStatus()) &&
                (me.getId().equals(target.getId()) ||
                 me.getEmpId().equals(target.getReportingOfficer()) ||
                 accessService.hasAny(me.getId(), "test.manage"));

        boolean canUpdateItems = me.getEmpId().equals(target.getReportingOfficer()) ||
                accessService.hasAny(me.getId(), "test.manage");

        model.addAttribute("plan", plan);
        model.addAttribute("employee", target);
        model.addAttribute("items", items);
        model.addAttribute("competencies", compMap);
        model.addAttribute("interventions", ivMap);
        model.addAttribute("canConfirm", canConfirm);
        model.addAttribute("canUpdateItems", canUpdateItems);
        return "plans/view";
    }

    @PostMapping("/plans/{id}/confirm")
    public String confirmPlan(@PathVariable("id") Long id,
                              @RequestParam(value = "note", required = false) String note,
                              Principal principal,
                              RedirectAttributes redirectAttributes) {
        Employee me = currentUser.get(principal);
        DevelopmentPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Development plan not found: " + id));
        Employee target = employeeRepository.findById(plan.getEmployeeId())
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        boolean allowed = me.getId().equals(target.getId()) ||
                me.getEmpId().equals(target.getReportingOfficer()) ||
                accessService.hasAny(me.getId(), "test.manage");

        if (!allowed) {
            throw new AccessDeniedException("You are not authorized to confirm this development plan.");
        }

        prescriptionService.confirmPlan(id, me, note);
        redirectAttributes.addFlashAttribute("msg", "Development plan confirmed successfully.");
        return "redirect:/plans/" + id;
    }

    @PostMapping("/plans/{id}/items/{itemId}/status")
    public String updateItemStatus(@PathVariable("id") Long planId,
                                   @PathVariable("itemId") Long itemId,
                                   @RequestParam("status") String status,
                                   Principal principal,
                                   RedirectAttributes redirectAttributes) {
        Employee me = currentUser.get(principal);
        DevelopmentPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Development plan not found: " + planId));
        Employee target = employeeRepository.findById(plan.getEmployeeId())
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        boolean allowed = me.getEmpId().equals(target.getReportingOfficer()) ||
                accessService.hasAny(me.getId(), "test.manage");
        if (!allowed) {
            throw new AccessDeniedException("You are not authorized to update development plan items.");
        }

        DevelopmentPlanItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));
        if (!item.getPlanId().equals(planId)) {
            throw new IllegalArgumentException("Item does not belong to specified plan.");
        }

        if ("open".equalsIgnoreCase(status) || "scheduled".equalsIgnoreCase(status) || "done".equalsIgnoreCase(status)) {
            item.setStatus(status.toLowerCase());
            itemRepository.save(item);
            auditService.record(me.getEmpId(), "item_status_updated", "development_plan_item", itemId.toString(),
                    "planId=" + planId + " status=" + status);
            redirectAttributes.addFlashAttribute("msg", "Item status updated to " + status + ".");
        }

        return "redirect:/plans/" + planId;
    }

    @GetMapping("/admin/interventions")
    public String catalogue(Principal principal, Model model) {
        currentUser.requireAny(principal, "framework.manage", "test.manage");
        List<Intervention> list = interventionRepository.findAll();
        list.sort((a, b) -> {
            String ca = a.getCompetencyCode() != null ? a.getCompetencyCode() : "ZZZ";
            String cb = b.getCompetencyCode() != null ? b.getCompetencyCode() : "ZZZ";
            return ca.compareTo(cb);
        });
        model.addAttribute("interventions", list);
        model.addAttribute("competencies", competencyRepository.findAll());
        return "admin/interventions";
    }

    @PostMapping("/admin/interventions")
    public String addIntervention(@RequestParam(value = "competencyCode", required = false) String compCode,
                                  @RequestParam(value = "category", required = false) String category,
                                  @RequestParam("gapType") String gapType,
                                  @RequestParam("titleEn") String titleEn,
                                  @RequestParam(value = "provider", defaultValue = "CoE") String provider,
                                  @RequestParam(value = "mode", defaultValue = "Classroom") String mode,
                                  @RequestParam(value = "durationHours", defaultValue = "4.0") BigDecimal durationHours,
                                  @RequestParam(value = "link", defaultValue = "") String link,
                                  Principal principal,
                                  RedirectAttributes redirectAttributes) {
        Employee me = currentUser.requireAny(principal, "framework.manage", "test.manage");

        String cc = (compCode != null && !compCode.isBlank() && !"ALL".equalsIgnoreCase(compCode)) ? compCode.trim() : null;
        String cat = (category != null && !category.isBlank() && !"ALL".equalsIgnoreCase(category)) ? category.trim() : null;

        Intervention iv = new Intervention(cc, cat, gapType.trim().toLowerCase(), titleEn.trim(), provider.trim(), mode.trim(), durationHours);
        iv.setLink(link != null ? link.trim() : "");
        iv = interventionRepository.save(iv);

        auditService.record(me.getEmpId(), "intervention_created", "intervention", iv.getId().toString(),
                "title=" + titleEn + " gapType=" + gapType);

        redirectAttributes.addFlashAttribute("msg", "Training intervention added successfully.");
        return "redirect:/admin/interventions";
    }

    @PostMapping("/admin/interventions/{id}/delete")
    public String deleteIntervention(@PathVariable("id") Long id,
                                     Principal principal,
                                     RedirectAttributes redirectAttributes) {
        Employee me = currentUser.requireAny(principal, "framework.manage", "test.manage");
        interventionRepository.findById(id).ifPresent(iv -> {
            interventionRepository.delete(iv);
            auditService.record(me.getEmpId(), "intervention_deleted", "intervention", id.toString(),
                    "title=" + iv.getTitleEn());
        });
        redirectAttributes.addFlashAttribute("msg", "Intervention removed from catalogue.");
        return "redirect:/admin/interventions";
    }

    private boolean canViewPlan(Employee viewer, Employee target) {
        if (viewer.getId().equals(target.getId())
                || accessService.hasAny(viewer.getId(), "org.view")
                || accessService.hasAny(viewer.getId(), "test.manage")) {
            return true;
        }
        if (accessService.hasAny(viewer.getId(), "team.view") && viewer.getEmpId().equals(target.getReportingOfficer())) {
            return true;
        }
        return accessService.can(viewer.getId(), "department.view", "DEPARTMENT", target.getDepartment());
    }
}
