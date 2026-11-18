package in.mahametro.competency.reporting;

/**
 * Filter parameters for searching and slicing staff results and dashboards.
 */
public class StaffFilter {

    private String q = "";
    private String department = "";
    private String roleFamilyCode = "";
    private String gradeCode = "";
    private String readiness = "";
    private String reportingOfficer = "";

    public StaffFilter() {
    }

    public String getQ() { return q; }
    public void setQ(String q) { this.q = q != null ? q.trim() : ""; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department != null ? department.trim() : ""; }

    public String getRoleFamilyCode() { return roleFamilyCode; }
    public void setRoleFamilyCode(String roleFamilyCode) { this.roleFamilyCode = roleFamilyCode != null ? roleFamilyCode.trim() : ""; }

    public String getGradeCode() { return gradeCode; }
    public void setGradeCode(String gradeCode) { this.gradeCode = gradeCode != null ? gradeCode.trim() : ""; }

    public String getReadiness() { return readiness; }
    public void setReadiness(String readiness) { this.readiness = readiness != null ? readiness.trim() : ""; }

    public String getReportingOfficer() { return reportingOfficer; }
    public void setReportingOfficer(String reportingOfficer) { this.reportingOfficer = reportingOfficer != null ? reportingOfficer.trim() : ""; }
}
