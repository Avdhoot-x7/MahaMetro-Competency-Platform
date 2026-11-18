package in.mahametro.competency.questions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import in.mahametro.competency.access.EmployeeRole;
import in.mahametro.competency.access.EmployeeRoleRepository;
import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.employees.EmployeeRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

/** Excel upload by a programmer, then approval by a subject expert. */
@SpringBootTest
@ActiveProfiles("test")
class QuestionWorkflowTests {

    @Autowired EmployeeRepository employees;
    @Autowired EmployeeRoleRepository employeeRoles;
    @Autowired QuestionImportService importer;
    @Autowired QuestionWorkflowService workflow;
    @Autowired QuestionRepository questions;
    @Autowired QuestionOptionRepository options;

    private Employee person(String empId, String role, String scopeType, String scopeValue) {
        Employee employee = employees.save(new Employee(empId, empId));
        employeeRoles.save(new EmployeeRole(employee.getId(), role, scopeType, scopeValue, "test"));
        return employee;
    }

    /** Builds an upload file: each row is topic, variation, question text, correct answers, wrong answers. */
    private ByteArrayInputStream workbook(String[][] rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);
            for (int i = 0; i < QuestionImportService.HEADINGS.length; i++) {
                header.createCell(i).setCellValue(QuestionImportService.HEADINGS[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                row.createCell(0).setCellValue(r + 1);
                row.createCell(3).setCellValue(rows[r][0]);
                row.createCell(18).setCellValue(rows[r][1]);
                row.createCell(4).setCellValue(rows[r][2]);
                String[] correct = rows[r][3].isEmpty() ? new String[0] : rows[r][3].split(";");
                for (int i = 0; i < correct.length; i++) {
                    row.createCell(5 + i).setCellValue(correct[i]);
                }
                String[] wrong = rows[r][4].isEmpty() ? new String[0] : rows[r][4].split(";");
                for (int i = 0; i < wrong.length; i++) {
                    row.createCell(9 + i).setCellValue(wrong[i]);
                }
                row.createCell(12).setCellValue(45);
                row.createCell(13).setCellValue(3);
                row.createCell(16).setCellValue("Because.");
            }
            wb.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private List<Question> inCompetency(String code) {
        List<Question> found = new ArrayList<>();
        for (Question q : questions.findAll(PageRequest.of(0, 500, Sort.by("id"))).getContent()) {
            if (q.getCompetencyCode().equals(code)) {
                found.add(q);
            }
        }
        return found;
    }

    @Test
    void uploadThenSubjectExpertApproves() throws Exception {
        Employee programmer = person("P-1", "programmer", "ALL", "");
        Employee expert = person("E-1", "subject_expert", "COMPETENCY", "C02");
        Employee otherExpert = person("E-2", "subject_expert", "COMPETENCY", "C03");

        ImportReport report = importer.importFile(programmer, "C02", workbook(new String[][] {
            {"1", "1", "Which line? <svg width=\"10\" height=\"10\"><rect width=\"10\" height=\"10\"/></svg>", "Aqua", "Red;Blue;Green"},
            {"1", "2", "Which lines?", "Aqua;Orange", "Red;Blue"},
            {"2", "1", "No correct answer here", "", "Red;Blue"},
            {"1", "2", "Same topic and variation again", "Aqua", "Red"}}));

        assertEquals(2, report.getCreated());
        assertEquals(2, report.getErrors().size());
        List<Question> added = inCompetency("C02");
        assertEquals(2, added.size());
        Question first = added.get(0);
        Question second = added.get(1);
        assertEquals(Question.SUBMITTED, first.getStatus());
        assertEquals("single", first.getQtype());
        assertEquals("multi", second.getQtype());
        assertEquals(45, first.getTimeSeconds());
        assertEquals(3, first.getTargetLevel());
        assertNull(first.getParentId());
        assertEquals(first.getId(), second.getParentId());
        assertEquals(4, options.findByQuestionIdOrderByOrdAsc(first.getId()).size());
        assertTrue(first.getCode().startsWith("C02-"));

        assertThrows(WorkflowException.class, () -> workflow.approve(otherExpert, first.getId(), ""));
        Question approved = workflow.approve(expert, first.getId(), "fine");
        assertEquals(Question.APPROVED, approved.getStatus());
        assertEquals("E-1", approved.getApproverEmpId());
        assertThrows(WorkflowException.class, () -> workflow.reject(expert, first.getId(), "too late"));
        assertEquals(Question.REJECTED, workflow.reject(expert, second.getId(), "unclear").getStatus());
    }

    @Test
    void nobodyApprovesTheirOwnQuestionAndUploadNeedsTheRole() throws Exception {
        Employee both = person("PE-1", "programmer", "ALL", "");
        employeeRoles.save(new EmployeeRole(both.getId(), "subject_expert", "ALL", "", "test"));
        Employee outsider = person("X-1", "supervisor", "ALL", "");
        String[][] rows = {{"7", "1", "Own question", "Yes", "No"}};

        assertEquals(0, importer.importFile(outsider, "C04", workbook(rows)).getCreated());
        assertEquals(1, importer.importFile(both, "C04", workbook(rows)).getCreated());
        Question own = inCompetency("C04").get(0);
        assertThrows(WorkflowException.class, () -> workflow.approve(both, own.getId(), ""));
    }
}
