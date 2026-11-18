package in.mahametro.competency.employees;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Maps a position/designation title from SAP HR to a standard role family and grade band.
 */
@Entity
@Table(name = "designation_map")
public class DesignationMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String designation;
    private String roleFamilyCode;
    private String gradeCode;

    protected DesignationMap() {
    }

    public DesignationMap(String designation, String roleFamilyCode, String gradeCode) {
        this.designation = designation != null ? designation.trim() : "";
        this.roleFamilyCode = roleFamilyCode != null && !roleFamilyCode.isBlank() ? roleFamilyCode.trim() : null;
        this.gradeCode = gradeCode != null && !gradeCode.isBlank() ? gradeCode.trim() : null;
    }

    public Long getId() { return id; }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation != null ? designation.trim() : ""; }

    public String getRoleFamilyCode() { return roleFamilyCode; }
    public void setRoleFamilyCode(String roleFamilyCode) {
        this.roleFamilyCode = roleFamilyCode != null && !roleFamilyCode.isBlank() ? roleFamilyCode.trim() : null;
    }

    public String getGradeCode() { return gradeCode; }
    public void setGradeCode(String gradeCode) {
        this.gradeCode = gradeCode != null && !gradeCode.isBlank() ? gradeCode.trim() : null;
    }
}
