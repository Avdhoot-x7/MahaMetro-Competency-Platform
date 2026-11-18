package in.mahametro.competency.questions;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A writing target given to a contributor, for example 6 questions for D07 at level 3 by a date. */
@Entity
@Table(name = "question_assignment")
public class QuestionAssignment {

    public static final String OPEN = "OPEN";
    public static final String DONE = "DONE";
    public static final String CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String contributorEmpId;
    private String competencyCode;
    private int targetLevel = 3;
    private int quantity = 1;
    private LocalDate dueDate;
    private String assignedBy = "";
    private String status = OPEN;
    private LocalDateTime createdAt = LocalDateTime.now();

    protected QuestionAssignment() {
    }

    public QuestionAssignment(String contributorEmpId, String competencyCode, int targetLevel, int quantity,
            LocalDate dueDate, String assignedBy) {
        this.contributorEmpId = contributorEmpId;
        this.competencyCode = competencyCode;
        this.targetLevel = targetLevel;
        this.quantity = quantity;
        this.dueDate = dueDate;
        this.assignedBy = assignedBy;
    }

    public Long getId() { return id; }
    public String getContributorEmpId() { return contributorEmpId; }
    public String getCompetencyCode() { return competencyCode; }
    public int getTargetLevel() { return targetLevel; }
    public int getQuantity() { return quantity; }
    public LocalDate getDueDate() { return dueDate; }
    public String getAssignedBy() { return assignedBy; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
