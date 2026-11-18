package in.mahametro.competency.scoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One measured score (0 to 100) for an employee, competency, cycle and method. */
@Entity
@Table(name = "competency_score")
public class CompetencyScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long employeeId;
    private String competencyCode;
    private String cycle;
    private String method;
    @Column(precision = 5, scale = 2)
    private BigDecimal score;
    private String recordedBy = "";
    private LocalDateTime recordedAt = LocalDateTime.now();

    protected CompetencyScore() {
    }

    public CompetencyScore(Long employeeId, String competencyCode, String cycle, String method, double score,
            String recordedBy) {
        this.employeeId = employeeId;
        this.competencyCode = competencyCode;
        this.cycle = cycle;
        this.method = method;
        this.score = BigDecimal.valueOf(score);
        this.recordedBy = recordedBy;
    }

    public Long getId() { return id; }
    public Long getEmployeeId() { return employeeId; }
    public String getCompetencyCode() { return competencyCode; }
    public String getCycle() { return cycle; }
    public String getMethod() { return method; }
    public BigDecimal getScore() { return score; }
    public String getRecordedBy() { return recordedBy; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
}
