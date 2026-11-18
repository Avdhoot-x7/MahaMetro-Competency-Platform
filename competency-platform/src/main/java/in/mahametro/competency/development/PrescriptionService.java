package in.mahametro.competency.development;

import in.mahametro.competency.core.AuditService;
import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.scoring.Profile;
import in.mahametro.competency.scoring.ProfileRow;
import in.mahametro.competency.scoring.ProfileService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns measured competency gaps into an Individual Development Plan (IDP).
 * Ported from Wise & Ma / Maha Metro prescription rules in Python pilot.
 */
@Service
public class PrescriptionService {

    public static final Map<String, Integer> TARGET_DAYS = Map.of(
            "safety", 30,
            "major", 60,
            "minor", 90,
            "behavioural", 90,
            "strength", 180
    );

    public static final Map<String, Integer> PRIORITY = Map.of(
            "safety", 1,
            "major", 2,
            "minor", 3,
            "behavioural", 4,
            "strength", 9
    );

    private final InterventionRepository interventionRepository;
    private final DevelopmentPlanRepository planRepository;
    private final DevelopmentPlanItemRepository itemRepository;
    private final ProfileService profileService;
    private final AuditService audit;

    public PrescriptionService(InterventionRepository interventionRepository,
                               DevelopmentPlanRepository planRepository,
                               DevelopmentPlanItemRepository itemRepository,
                               ProfileService profileService,
                               AuditService audit) {
        this.interventionRepository = interventionRepository;
        this.planRepository = planRepository;
        this.itemRepository = itemRepository;
        this.profileService = profileService;
        this.audit = audit;
    }

    /**
     * Determines gap type for a competency in the candidate's profile.
     */
    public String gapType(ProfileRow row) {
        if (!row.isAssessed()) {
            return null;
        }
        if (row.getGap() <= 0) {
            return row.getLevel() >= 4 ? "strength" : null;
        }
        if (row.isSafetyCritical()) {
            return "safety";
        }
        if ("Behavioural".equalsIgnoreCase(row.getCategory())) {
            return "behavioural";
        }
        return row.getGap() >= 2 ? "major" : "minor";
    }

    /**
     * Picks the most specific matching training intervention from the catalogue.
     */
    public Intervention pickIntervention(ProfileRow row, String gtype) {
        // 1. Exact match on competency and gap type
        Optional<Intervention> specific = interventionRepository.findFirstByCompetencyCodeAndGapType(row.getCode(), gtype);
        if (specific.isPresent()) {
            return specific.get();
        }

        // 2. Competency match for any gap (except strength)
        if (!"strength".equalsIgnoreCase(gtype)) {
            Optional<Intervention> compAny = interventionRepository.findFirstByCompetencyCode(row.getCode());
            if (compAny.isPresent()) {
                return compAny.get();
            }
        }

        // 3. Generic fallback by category and gap type
        List<Intervention> generics = interventionRepository.findByCompetencyCodeIsNullAndGapType(gtype);
        for (Intervention iv : generics) {
            if (iv.getCategory() == null || iv.getCategory().equalsIgnoreCase(row.getCategory())) {
                return iv;
            }
        }
        return generics.isEmpty() ? null : generics.get(0);
    }

    /**
     * Generates an Individual Development Plan (IDP) from the employee's current measured profile.
     */
    @Transactional
    public DevelopmentPlan generatePlan(Employee emp, String cycle) {
        Profile profile = profileService.build(emp);

        // Supersede any existing proposed plans for the same cycle
        List<DevelopmentPlan> oldPlans = planRepository.findByEmployeeIdAndCycleAndStatus(
                emp.getId(), cycle, DevelopmentPlan.STATUS_PROPOSED);
        for (DevelopmentPlan old : oldPlans) {
            itemRepository.deleteByPlanId(old.getId());
            planRepository.delete(old);
        }

        BigDecimal index = BigDecimal.valueOf(profile.getIndex() != null ? profile.getIndex() : 0.0);
        DevelopmentPlan plan = new DevelopmentPlan(emp.getId(), cycle, profile.getReadiness(), index);
        plan = planRepository.save(plan);

        LocalDateTime now = LocalDateTime.now();
        List<DevelopmentPlanItem> items = new ArrayList<>();
        List<DevelopmentPlanItem> strengthItems = new ArrayList<>();

        for (ProfileRow row : profile.getRows()) {
            String gtype = gapType(row);
            if (gtype == null) {
                continue;
            }

            Intervention iv = pickIntervention(row, gtype);
            int priority = PRIORITY.getOrDefault(gtype, 5);
            int days = TARGET_DAYS.getOrDefault(gtype, 90);
            LocalDateTime targetDate = "strength".equalsIgnoreCase(gtype) ? null : now.plusDays(days);
            Double scoreVal = row.getScore() != null ? row.getScore() : 0.0;

            DevelopmentPlanItem item = new DevelopmentPlanItem(
                    plan.getId(),
                    row.getCode(),
                    row.getRequired(),
                    row.getLevel(),
                    BigDecimal.valueOf(scoreVal),
                    row.getGap(),
                    priority,
                    gtype,
                    iv != null ? iv.getId() : null,
                    targetDate
            );

            if ("strength".equalsIgnoreCase(gtype)) {
                strengthItems.add(item);
            } else {
                items.add(item);
            }
        }

        // Sort gap items: priority asc (safety first), then gap desc (largest gap first), then code asc
        items.sort(Comparator.comparingInt(DevelopmentPlanItem::getPriority)
                .thenComparing((DevelopmentPlanItem a, DevelopmentPlanItem b) -> Integer.compare(b.getGap(), a.getGap()))
                .thenComparing(DevelopmentPlanItem::getCompetencyCode));

        // Limit top 3 strengths for mentorship / SME author nomination
        strengthItems.sort((a, b) -> b.getScore().compareTo(a.getScore()));
        List<DevelopmentPlanItem> topStrengths = strengthItems.subList(0, Math.min(3, strengthItems.size()));

        items.addAll(topStrengths);
        itemRepository.saveAll(items);

        audit.record(emp.getEmpId(), "plan_generated", "development_plan", plan.getId().toString(),
                "cycle=" + cycle + " items=" + items.size() + " readiness=" + plan.getReadiness());
        return plan;
    }

    /**
     * Confirms a proposed development plan by supervisor or Assessment Cell.
     */
    @Transactional
    public DevelopmentPlan confirmPlan(Long planId, Employee supervisor, String note) {
        DevelopmentPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Development plan not found: " + planId));

        plan.setStatus(DevelopmentPlan.STATUS_CONFIRMED);
        plan.setConfirmedBy(supervisor.getEmpId());
        plan.setConfirmedAt(LocalDateTime.now());
        DevelopmentPlan saved = planRepository.save(plan);

        audit.record(supervisor.getEmpId(), "plan_confirmed", "development_plan", planId.toString(),
                note == null ? "" : note);
        return saved;
    }

    public Optional<DevelopmentPlan> getLatestPlan(Long employeeId) {
        return planRepository.findFirstByEmployeeIdOrderByCreatedAtDescIdDesc(employeeId);
    }

    public List<DevelopmentPlanItem> getItems(Long planId) {
        return itemRepository.findByPlanIdOrderByPriorityAscGapDescCompetencyCodeAsc(planId);
    }
}
