package in.mahametro.competency.tests;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Rule specifying how many questions to draw for a competency and target level. */
@Entity
@Table(name = "blueprint_rule")
public class BlueprintRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long blueprintId;
    private String competencyCode;
    private int itemCount = 1;
    private int targetLevel = 3;

    protected BlueprintRule() {
    }

    public BlueprintRule(Long blueprintId, String competencyCode, int itemCount, int targetLevel) {
        this.blueprintId = blueprintId;
        this.competencyCode = competencyCode;
        this.itemCount = itemCount;
        this.targetLevel = targetLevel;
    }

    public Long getId() {
        return id;
    }

    public Long getBlueprintId() {
        return blueprintId;
    }

    public void setBlueprintId(Long blueprintId) {
        this.blueprintId = blueprintId;
    }

    public String getCompetencyCode() {
        return competencyCode;
    }

    public void setCompetencyCode(String competencyCode) {
        this.competencyCode = competencyCode;
    }

    public int getItemCount() {
        return itemCount;
    }

    public void setItemCount(int itemCount) {
        this.itemCount = itemCount;
    }

    public int getTargetLevel() {
        return targetLevel;
    }

    public void setTargetLevel(int targetLevel) {
        this.targetLevel = targetLevel;
    }
}
