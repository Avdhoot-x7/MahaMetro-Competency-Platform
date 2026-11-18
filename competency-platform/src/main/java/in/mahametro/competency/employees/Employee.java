package in.mahametro.competency.employees;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** One member of staff. empId is the SAP personnel number and is the login name. */
@Entity
@Table(name = "employee")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String empId;
    private String nameEn;
    private String nameMr = "";
    private String email = "";
    private String mobile = "";
    private String designation = "";
    private String department = "";
    private String location = "";
    private String roleFamilyCode;
    private String gradeCode;
    private String reportingOfficer = "";
    private String preferredLang = "en";
    private int extraTimePct;
    private String passwordHash;
    private boolean active = true;
    private LocalDateTime consentAt;
    private LocalDateTime lastLogin;
    private String source = "manual";
    private int failedLogins;
    private LocalDateTime lockedUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected Employee() {
    }

    public Employee(String empId, String nameEn) {
        this.empId = empId;
        this.nameEn = nameEn;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getEmpId() { return empId; }
    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }
    public String getNameMr() { return nameMr; }
    public void setNameMr(String nameMr) { this.nameMr = nameMr; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getRoleFamilyCode() { return roleFamilyCode; }
    public void setRoleFamilyCode(String roleFamilyCode) { this.roleFamilyCode = roleFamilyCode; }
    public String getGradeCode() { return gradeCode; }
    public void setGradeCode(String gradeCode) { this.gradeCode = gradeCode; }
    public String getReportingOfficer() { return reportingOfficer; }
    public void setReportingOfficer(String reportingOfficer) { this.reportingOfficer = reportingOfficer; }
    public String getPreferredLang() { return preferredLang; }
    public void setPreferredLang(String preferredLang) { this.preferredLang = preferredLang; }
    public int getExtraTimePct() { return extraTimePct; }
    public void setExtraTimePct(int extraTimePct) { this.extraTimePct = extraTimePct; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getConsentAt() { return consentAt; }
    public void setConsentAt(LocalDateTime consentAt) { this.consentAt = consentAt; }
    public LocalDateTime getLastLogin() { return lastLogin; }
    public void setLastLogin(LocalDateTime lastLogin) { this.lastLogin = lastLogin; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public int getFailedLogins() { return failedLogins; }
    public void setFailedLogins(int failedLogins) { this.failedLogins = failedLogins; }
    public LocalDateTime getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(LocalDateTime lockedUntil) { this.lockedUntil = lockedUntil; }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
