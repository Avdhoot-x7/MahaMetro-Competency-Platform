package in.mahametro.competency.development;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A single prescribed intervention item in an Individual Development Plan.
 */
@Entity
@Table(name = "development_plan_item")
public class DevelopmentPlanItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long planId;
    private String competencyCode;
    private int requiredLevel;
    private int attainedLevel;
    private BigDecimal score = BigDecimal.ZERO;
    private int gap;
    private int priority; // 1 safety (30d), 2 major (60d), 3 minor (90d), 4 behavioural (90d), 9 strength (180d)
    private String gapType;
    private Long interventionId;
    private LocalDateTime targetDate;
    private String status = "open"; // open | scheduled | done

    protected DevelopmentPlanItem() {
    }

    public DevelopmentPlanItem(Long planId, String competencyCode, int requiredLevel, int attainedLevel,
                               BigDecimal score, int gap, int priority, String gapType, Long interventionId,
                               LocalDateTime targetDate) {
        this.planId = planId;
        this.competencyCode = competencyCode;
        this.requiredLevel = requiredLevel;
        this.attainedLevel = attainedLevel;
        this.score = score;
        this.gap = gap;
        this.priority = priority;
        this.gapType = gapType;
        this.interventionId = interventionId;
        this.targetDate = targetDate;
    }

    public Long getId() { return id; }
    public Long getPlanId() { return planId; }
    public String getCompetencyCode() { return competencyCode; }
    public int getRequiredLevel() { return requiredLevel; }
    public int getAttainedLevel() { return attainedLevel; }
    public BigDecimal getScore() { return score; }
    public int getGap() { return gap; }
    public int getPriority() { return priority; }
    public String getGapType() { return gapType; }
    public Long getInterventionId() { return interventionId; }
    public void setInterventionId(Long interventionId) { this.interventionId = interventionId; }
    public LocalDateTime getTargetDate() { return targetDate; }
    public void setTargetDate(LocalDateTime targetDate) { this.targetDate = targetDate; }
    public int getCurrentLevel() { return attainedLevel; }
    public int getTargetLevel() { return requiredLevel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
