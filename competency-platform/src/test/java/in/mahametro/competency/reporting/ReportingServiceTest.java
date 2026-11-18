package in.mahametro.competency.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import in.mahametro.competency.access.EmployeeRole;
import in.mahametro.competency.access.EmployeeRoleRepository;
import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.employees.EmployeeRepository;
import in.mahametro.competency.scoring.CompetencyScore;
import in.mahametro.competency.scoring.CompetencyScoreRepository;
import java.io.ByteArrayInputStream;
import java.util.Map;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReportingServiceTest {

    @Autowired ReportingService reportingService;
    @Autowired EmployeeRepository employees;
    @Autowired EmployeeRoleRepository employeeRoles;
    @Autowired CompetencyScoreRepository scores;
    @Autowired ProfileSummaryRepository summaries;

    @Test
    void testProfileSummaryPersistence() {
        Employee emp = employees.save(new Employee("SUM-101", "Summary Candidate"));
        emp.setRoleFamilyCode("SC");
        emp.setGradeCode("G1");
        emp = employees.save(emp);

        scores.save(new CompetencyScore(emp.getId(), "C01", "T0", "knowledge", 80.0, "test"));
        scores.save(new CompetencyScore(emp.getId(), "C02", "T0", "knowledge", 75.0, "test"));

        ProfileSummary summary = reportingService.refreshSummary(emp.getId());
        assertNotNull(summary);
        assertEquals(emp.getId(), summary.getEmployeeId());
        assertNotNull(summary.getReadiness());
        assertTrue(summary.getAssessedCount() >= 2);

        ProfileSummary fromDb = summaries.findById(emp.getId()).orElse(null);
        assertNotNull(fromDb);
        assertEquals(summary.getReadiness(), fromDb.getReadiness());
    }

    @Test
    void testSupervisorTeamMetrics() {
        Employee sup = employees.save(new Employee("SUP-801", "Team Lead"));
        sup.setRoleFamilyCode("SC");
        sup.setGradeCode("G3");
        employees.save(sup);
        employeeRoles.save(new EmployeeRole(sup.getId(), "supervisor", "ALL", "", "admin"));

        Employee m1 = employees.save(new Employee("MEM-801", "Member One"));
        m1.setRoleFamilyCode("SC");
        m1.setGradeCode("G1");
        m1.setReportingOfficer("SUP-801");
        employees.save(m1);
        scores.save(new CompetencyScore(m1.getId(), "C01", "T0", "knowledge", 40.0, "test")); // Low score -> gap
        reportingService.refreshSummary(m1.getId());

        Employee m2 = employees.save(new Employee("MEM-802", "Member Two"));
        m2.setRoleFamilyCode("SC");
        m2.setGradeCode("G1");
        m2.setReportingOfficer("SUP-801");
        employees.save(m2);
        scores.save(new CompetencyScore(m2.getId(), "C01", "T0", "knowledge", 90.0, "test"));
        reportingService.refreshSummary(m2.getId());

        DashboardSummary dashboard = reportingService.getSupervisorDashboard(sup);
        assertNotNull(dashboard);
        assertEquals(2, dashboard.getTotalPeople());
        assertEquals(2, dashboard.getAssessedPeople());
        assertTrue(dashboard.getCoveragePct() >= 99.0);
        assertTrue(dashboard.getRedCount() + dashboard.getAmberCount() + dashboard.getGreenCount() >= 2);
    }

    @Test
    void testDepartmentAndOrgScorecards() {
        Employee e1 = employees.save(new Employee("DPT-101", "Dept Member 1"));
        e1.setDepartment("Operations");
        e1.setRoleFamilyCode("SC");
        e1.setGradeCode("G2");
        employees.save(e1);
        scores.save(new CompetencyScore(e1.getId(), "C01", "T0", "knowledge", 85.0, "test"));
        reportingService.refreshSummary(e1.getId());

        Employee e2 = employees.save(new Employee("DPT-102", "Dept Member 2"));
        e2.setDepartment("Operations");
        e2.setRoleFamilyCode("TO");
        e2.setGradeCode("G1");
        employees.save(e2);
        scores.save(new CompetencyScore(e2.getId(), "C01", "T0", "knowledge", 30.0, "test"));
        reportingService.refreshSummary(e2.getId());

        // Department dashboard
        DashboardSummary deptSummary = reportingService.getDepartmentDashboard("Operations");
        assertNotNull(deptSummary);
        assertTrue(deptSummary.getTotalPeople() >= 2);
        assertFalse(deptSummary.getBreakdowns().isEmpty());

        // Org dashboard
        DashboardSummary orgSummary = reportingService.getOrgDashboard();
        assertNotNull(orgSummary);
        assertTrue(orgSummary.getTotalPeople() >= 2);
        assertFalse(orgSummary.getBreakdowns().isEmpty());
    }

    @Test
    void testExcelExportGeneration() throws Exception {
        Employee admin = employees.save(new Employee("ADMIN-REP", "Admin Reporter"));
        employeeRoles.save(new EmployeeRole(admin.getId(), "superadmin", "ALL", "", "admin"));

        Employee e = employees.save(new Employee("EXP-101", "Export Candidate"));
        e.setRoleFamilyCode("SC");
        e.setGradeCode("G1");
        e.setDepartment("Operations");
        employees.save(e);
        scores.save(new CompetencyScore(e.getId(), "C01", "T0", "knowledge", 85.0, "test"));
        reportingService.refreshSummary(e.getId());

        byte[] excelBytes = reportingService.exportStaffResults(admin, new StaffFilter());
        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 1000);

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet sheet = wb.getSheet("Staff Results");
            assertNotNull(sheet);
            assertEquals("Employee ID", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("Readiness", sheet.getRow(0).getCell(9).getStringCellValue());
        }
    }

    @Test
    void testPagedStaffResultsAndFiltering() {
        Employee admin = employees.save(new Employee("ADMIN-PAG", "Admin Pager"));
        employeeRoles.save(new EmployeeRole(admin.getId(), "superadmin", "ALL", "", "admin"));

        Employee empRed = employees.save(new Employee("PAG-RED", "Red Staff"));
        empRed.setRoleFamilyCode("SC");
        empRed.setGradeCode("G1");
        empRed.setDepartment("Operations");
        employees.save(empRed);
        // Low score on safety critical C01 -> Red
        scores.save(new CompetencyScore(empRed.getId(), "C01", "T0", "knowledge", 20.0, "test"));
        reportingService.refreshSummary(empRed.getId());

        StaffFilter redFilter = new StaffFilter();
        redFilter.setReadiness("Red");
        Page<StaffSummaryRow> pageResult = reportingService.getPagedStaffResults(admin, redFilter, PageRequest.of(0, 10));

        assertNotNull(pageResult);
        assertTrue(pageResult.getContent().stream().anyMatch(r -> "PAG-RED".equals(r.getEmpId())));

        Map<String, Integer> counts = reportingService.getHeadlineCounts(admin, new StaffFilter());
        assertNotNull(counts);
        assertTrue(counts.get("red") >= 1);
    }
}
