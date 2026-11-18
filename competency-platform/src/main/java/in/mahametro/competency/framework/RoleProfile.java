package in.mahametro.competency.framework;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** The base level (1 to 5) a role family needs in one competency. */
@Entity
@Table(name = "role_profile")
public class RoleProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String roleFamilyCode;
    private String competencyCode;
    private int baseLevel;
    private String approvedBy;
    private LocalDateTime approvedAt;

    protected RoleProfile() {
    }

    public RoleProfile(String roleFamilyCode, String competencyCode, int baseLevel) {
        this.roleFamilyCode = roleFamilyCode;
        this.competencyCode = competencyCode;
        this.baseLevel = baseLevel;
    }

    public Long getId() { return id; }
    public String getRoleFamilyCode() { return roleFamilyCode; }
    public String getCompetencyCode() { return competencyCode; }
    public int getBaseLevel() { return baseLevel; }
    public void setBaseLevel(int baseLevel) { this.baseLevel = baseLevel; }
    public String getApprovedBy() { return approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }

    public void approve(String empId) {
        this.approvedBy = empId;
        this.approvedAt = LocalDateTime.now();
    }
}
