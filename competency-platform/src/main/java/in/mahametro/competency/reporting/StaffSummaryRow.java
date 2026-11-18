package in.mahametro.competency.reporting;

import in.mahametro.competency.employees.Employee;

/**
 * Combined view model representing an employee record with its pre-calculated competency profile summary.
 */
public class StaffSummaryRow {

    private Long id;
    private String empId;
    private String nameEn;
    private String nameMr;
    private String designation;
    private String department;
    private String location;
    private String roleFamilyCode;
    private String gradeCode;
    private String reportingOfficer;
    private String readiness;
    private Double indexPct;
    private int assessedCount;
    private int gapsCount;
    private int safetyGates;

    public StaffSummaryRow() {
    }

    public StaffSummaryRow(Employee e, ProfileSummary s) {
        this.id = e.getId();
        this.empId = e.getEmpId();
        this.nameEn = e.getNameEn();
        this.nameMr = e.getNameMr();
        this.designation = e.getDesignation();
        this.department = e.getDepartment();
        this.location = e.getLocation();
        this.roleFamilyCode = e.getRoleFamilyCode();
        this.gradeCode = e.getGradeCode();
        this.reportingOfficer = e.getReportingOfficer();
        if (s != null) {
            this.readiness = s.getReadiness();
            this.indexPct = s.getIndexPct();
            this.assessedCount = s.getAssessedCount();
            this.gapsCount = s.getGapsCount();
            this.safetyGates = s.getSafetyGates();
        } else {
            this.readiness = "Not assessed";
            this.indexPct = null;
            this.assessedCount = 0;
            this.gapsCount = 0;
            this.safetyGates = 0;
        }
    }

    public Long getId() { return id; }
    public String getEmpId() { return empId; }
    public String getNameEn() { return nameEn; }
    public String getNameMr() { return nameMr; }
    public String getDesignation() { return designation; }
    public String getDepartment() { return department; }
    public String getLocation() { return location; }
    public String getRoleFamilyCode() { return roleFamilyCode; }
    public String getGradeCode() { return gradeCode; }
    public String getReportingOfficer() { return reportingOfficer; }
    public String getReadiness() { return readiness; }
    public Double getIndexPct() { return indexPct; }
    public int getAssessedCount() { return assessedCount; }
    public int getGapsCount() { return gapsCount; }
    public int getSafetyGates() { return safetyGates; }
}
