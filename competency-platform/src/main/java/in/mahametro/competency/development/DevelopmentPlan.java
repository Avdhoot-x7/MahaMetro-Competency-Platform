package in.mahametro.competency.development;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * An Individual Development Plan (IDP) prescribed for an employee.
 */
@Entity
@Table(name = "development_plan")
public class DevelopmentPlan {

    public static final String STATUS_PROPOSED = "proposed";
    public static final String STATUS_CONFIRMED = "confirmed";
    public static final String STATUS_CLOSED = "closed";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long employeeId;
    private String cycle = "T0";
    private String readiness = "Amber";
    private BigDecimal competencyIndex = BigDecimal.ZERO;
    private String status = STATUS_PROPOSED;
    private String confirmedBy = "";
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected DevelopmentPlan() {
    }

    public DevelopmentPlan(Long employeeId, String cycle, String readiness, BigDecimal competencyIndex) {
        this.employeeId = employeeId;
        this.cycle = cycle;
        this.readiness = readiness;
        this.competencyIndex = competencyIndex;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getEmployeeId() { return employeeId; }
    public String getCycle() { return cycle; }
    public void setCycle(String cycle) { this.cycle = cycle; }
    public String getReadiness() { return readiness; }
    public void setReadiness(String readiness) { this.readiness = readiness; }
    public BigDecimal getCompetencyIndex() { return competencyIndex; }
    public BigDecimal getIndexValue() { return competencyIndex; }
    public void setCompetencyIndex(BigDecimal competencyIndex) { this.competencyIndex = competencyIndex; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(String confirmedBy) { this.confirmedBy = confirmedBy; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public boolean isConfirmed() {
        return STATUS_CONFIRMED.equalsIgnoreCase(status);
    }
}
