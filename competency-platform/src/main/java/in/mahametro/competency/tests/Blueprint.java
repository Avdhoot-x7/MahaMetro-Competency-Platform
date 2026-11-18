package in.mahametro.competency.tests;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** A blueprint specifying test composition rules for roles or grades. */
@Entity
@Table(name = "blueprint")
public class Blueprint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String roleFamilyCode;
    private String gradeCode;
    private String notes = "";
    private LocalDateTime createdAt;

    protected Blueprint() {
    }

    public Blueprint(String name, String roleFamilyCode, String gradeCode) {
        this.name = name;
        this.roleFamilyCode = roleFamilyCode;
        this.gradeCode = gradeCode;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRoleFamilyCode() {
        return roleFamilyCode;
    }

    public void setRoleFamilyCode(String roleFamilyCode) {
        this.roleFamilyCode = roleFamilyCode;
    }

    public String getGradeCode() {
        return gradeCode;
    }

    public void setGradeCode(String gradeCode) {
        this.gradeCode = gradeCode;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
