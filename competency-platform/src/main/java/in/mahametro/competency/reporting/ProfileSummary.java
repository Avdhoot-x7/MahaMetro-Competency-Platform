package in.mahametro.competency.reporting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Pre-aggregated competency profile summary for an employee.
 * Powers high-performance dashboards, filtering, and reporting across 25,000 staff.
 */
@Entity
@Table(name = "profile_summary")
public class ProfileSummary {

    @Id
    @Column(name = "employee_id")
    private Long employeeId;

    @Column(nullable = false, length = 20)
    private String readiness = "Not assessed";

    @Column(name = "index_pct", precision = 5, scale = 1)
    private java.math.BigDecimal indexPct;

    @Column(name = "assessed_count", nullable = false)
    private int assessedCount;

    @Column(name = "gaps_count", nullable = false)
    private int gapsCount;

    @Column(name = "safety_gates", nullable = false)
    private int safetyGates;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ProfileSummary() {
    }

    public ProfileSummary(Long employeeId, String readiness, Double indexPct, int assessedCount, int gapsCount, int safetyGates) {
        this.employeeId = employeeId;
        this.readiness = readiness != null ? readiness : "Not assessed";
        this.indexPct = indexPct != null ? java.math.BigDecimal.valueOf(indexPct) : null;
        this.assessedCount = assessedCount;
        this.gapsCount = gapsCount;
        this.safetyGates = safetyGates;
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    @PreUpdate
    void onSave() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public String getReadiness() { return readiness; }
    public void setReadiness(String readiness) { this.readiness = readiness != null ? readiness : "Not assessed"; }

    public Double getIndexPct() { return indexPct != null ? indexPct.doubleValue() : null; }
    public void setIndexPct(Double indexPct) { this.indexPct = indexPct != null ? java.math.BigDecimal.valueOf(indexPct) : null; }
    public java.math.BigDecimal getIndexPctBigDecimal() { return indexPct; }
    public void setIndexPctBigDecimal(java.math.BigDecimal indexPct) { this.indexPct = indexPct; }

    public int getAssessedCount() { return assessedCount; }
    public void setAssessedCount(int assessedCount) { this.assessedCount = assessedCount; }

    public int getGapsCount() { return gapsCount; }
    public void setGapsCount(int gapsCount) { this.gapsCount = gapsCount; }

    public int getSafetyGates() { return safetyGates; }
    public void setSafetyGates(int safetyGates) { this.safetyGates = safetyGates; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
