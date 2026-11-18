package in.mahametro.competency.importers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import in.mahametro.competency.access.EmployeeRole;
import in.mahametro.competency.access.EmployeeRoleRepository;
import in.mahametro.competency.employees.DesignationMap;
import in.mahametro.competency.employees.DesignationMapRepository;
import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.employees.EmployeeRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmployeeImportServiceTest {

    @Autowired EmployeeImportService importService;
    @Autowired EmployeeRepository employees;
    @Autowired EmployeeRoleRepository employeeRoles;
    @Autowired DesignationMapRepository designationMaps;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void testCsvDryRunValidation() throws Exception {
        Employee actor = employees.save(new Employee("ACTOR-1", "Actor Admin"));

        String csv = "Employee ID,Name,Designation,Department,Location,Grade,Role family\n"
                + "NEW-901,Prakash Shinde,Technician,Maintenance,Nagpur,G1,MNT\n"
                + "INVALID-ROW,,Technician,Maintenance,Nagpur,G1,MNT\n"; // Missing name

        ByteArrayInputStream in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        EmployeeImportReport report = importService.importEmployees(in, "test.csv", actor, true);

        assertTrue(report.isDryRun());
        assertEquals(1, report.getCreated());
        assertEquals(0, report.getUpdated());
        assertEquals(1, report.getSkipped());
        assertEquals(1, report.getErrors().size());
        assertTrue(report.getErrors().get(0).contains("employee ID and name are required"));

        // Dry run must NOT persist to database
        assertFalse(employees.findByEmpId("NEW-901").isPresent());
    }

    @Test
    void testCsvLiveImportAndRoleAssignment() throws Exception {
        Employee actor = employees.save(new Employee("ACTOR-2", "Actor Admin"));

        String csv = "Employee ID,Name,Designation,Department,Location,Grade,Role family,Active\n"
                + "LIVE-101,Ramesh Kadam,Station Controller,Operations,Pune,G2,SC,Y\n";

        ByteArrayInputStream in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        EmployeeImportReport report = importService.importEmployees(in, "live.csv", actor, false);

        assertFalse(report.isDryRun());
        assertEquals(1, report.getCreated());
        assertEquals(0, report.getErrors().size());

        Optional<Employee> savedOpt = employees.findByEmpId("LIVE-101");
        assertTrue(savedOpt.isPresent());
        Employee saved = savedOpt.get();
        assertEquals("Ramesh Kadam", saved.getNameEn());
        assertEquals("SC", saved.getRoleFamilyCode());
        assertEquals("G2", saved.getGradeCode());
        assertEquals("sap_hr", saved.getSource());
        assertTrue(saved.isActive());
        assertTrue(passwordEncoder.matches(EmployeeImportService.DEFAULT_PASSWORD, saved.getPasswordHash()));

        // Check employee role assigned
        boolean hasEmployeeRole = employeeRoles.findByEmployeeId(saved.getId()).stream()
                .anyMatch(r -> "employee".equals(r.getRoleCode()));
        assertTrue(hasEmployeeRole);
    }

    @Test
    void testDesignationMappingResolution() throws Exception {
        Employee actor = employees.save(new Employee("ACTOR-3", "Actor Admin"));
        designationMaps.save(new DesignationMap("Traction Substation Engineer", "TPS", "G3"));

        // CSV does NOT provide Role family or Grade; should resolve via designation map
        String csv = "Employee ID,Name,Designation,Department,Location\n"
                + "DM-101,Sanjay Jadhav,Traction Substation Engineer,Electrical,Pune\n";

        ByteArrayInputStream in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        EmployeeImportReport report = importService.importEmployees(in, "dm.csv", actor, false);

        assertEquals(1, report.getCreated());
        assertEquals(0, report.getErrors().size());

        Employee saved = employees.findByEmpId("DM-101").orElseThrow();
        assertEquals("TPS", saved.getRoleFamilyCode());
        assertEquals("G3", saved.getGradeCode());
    }

    @Test
    void testSupervisorAutoPromotion() throws Exception {
        Employee actor = employees.save(new Employee("ACTOR-4", "Actor Admin"));

        // RO exists in DB without supervisor role
        Employee ro = employees.save(new Employee("RO-777", "Supervisor Candidate"));

        String csv = "Employee ID,Name,Designation,Reporting Officer\n"
                + "SUB-101,Team Member 1,Trackman,RO-777\n";

        ByteArrayInputStream in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        EmployeeImportReport report = importService.importEmployees(in, "team.csv", actor, false);

        assertEquals(1, report.getCreated());

        boolean isSupervisor = employeeRoles.findByEmployeeId(ro.getId()).stream()
                .anyMatch(r -> "supervisor".equals(r.getRoleCode()));
        assertTrue(isSupervisor, "Reporting officer RO-777 should receive supervisor role");
    }

    @Test
    void testExcelImportAndTemplate() throws Exception {
        Employee actor = employees.save(new Employee("ACTOR-5", "Actor Admin"));

        // 1. Template generation
        byte[] templateBytes = importService.generateTemplate();
        assertNotNull(templateBytes);
        assertTrue(templateBytes.length > 1000);

        // 2. Excel import
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Employees");
            Row h = sheet.createRow(0);
            h.createCell(0).setCellValue("Employee ID");
            h.createCell(1).setCellValue("Name");
            h.createCell(2).setCellValue("Designation");
            h.createCell(3).setCellValue("Role family");
            h.createCell(4).setCellValue("Grade");

            Row r = sheet.createRow(1);
            r.createCell(0).setCellValue("XLS-202");
            r.createCell(1).setCellValue("Deepak Chavan");
            r.createCell(2).setCellValue("Signalling Engineer");
            r.createCell(3).setCellValue("STA");
            r.createCell(4).setCellValue("G2");

            wb.write(out);
        }

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        EmployeeImportReport report = importService.importEmployees(in, "upload.xlsx", actor, false);

        assertEquals(1, report.getCreated());
        assertEquals(0, report.getErrors().size());

        Employee saved = employees.findByEmpId("XLS-202").orElseThrow();
        assertEquals("Deepak Chavan", saved.getNameEn());
        assertEquals("STA", saved.getRoleFamilyCode());
        assertEquals("G2", saved.getGradeCode());
    }
}
