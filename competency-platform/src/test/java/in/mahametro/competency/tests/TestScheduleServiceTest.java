package in.mahametro.competency.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.employees.EmployeeRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TestScheduleServiceTest {

    @Autowired
    TestService testService;

    @Autowired
    AssessmentTestRepository testRepository;

    @Autowired
    EmployeeRepository employeeRepository;

    private Employee cellOfficer;
    private Employee empOperations;
    private Employee empFinance;

    @BeforeEach
    void setUp() {
        cellOfficer = employeeRepository.save(new Employee("CELL-TEST-" + System.nanoTime(), "Cell Officer"));
        empOperations = new Employee("EMP-OPS-" + System.nanoTime(), "Ops Employee");
        empOperations.setDepartment("Operations");
        empOperations.setRoleFamilyCode("SC");
        empOperations.setGradeCode("G1");
        empOperations = employeeRepository.save(empOperations);

        empFinance = new Employee("EMP-FIN-" + System.nanoTime(), "Fin Employee");
        empFinance.setDepartment("Finance");
        empFinance.setRoleFamilyCode("FA");
        empFinance.setGradeCode("G2");
        empFinance = employeeRepository.save(empFinance);
    }

    @Test
    void testLifecycleWorkflow() {
        String code = "TEST-LIFE-" + System.nanoTime();
        AssessmentTest test = new AssessmentTest(code, "Operations Baseline", AssessmentTest.KIND_T0, 90);
        AssessmentTest created = testService.createTest(test, cellOfficer);

        assertNotNull(created.getId());
        assertEquals(AssessmentTest.STATUS_DRAFT, created.getStatus());

        // Publish
        AssessmentTest published = testService.publishTest(created.getId(), cellOfficer);
        assertEquals(AssessmentTest.STATUS_PUBLISHED, published.getStatus());

        // Close
        AssessmentTest closed = testService.closeTest(created.getId(), cellOfficer);
        assertEquals(AssessmentTest.STATUS_CLOSED, closed.getStatus());
    }

    @Test
    void rejectsDuplicateTestCode() {
        String code = "DUP-CODE-" + System.nanoTime();
        AssessmentTest t1 = new AssessmentTest(code, "Title 1", AssessmentTest.KIND_T0, 60);
        testService.createTest(t1, cellOfficer);

        AssessmentTest t2 = new AssessmentTest(code, "Title 2", AssessmentTest.KIND_T0, 60);
        assertThrows(IllegalArgumentException.class, () -> testService.createTest(t2, cellOfficer));
    }

    @Test
    void eligibilityByDepartment() {
        String code = "TEST-DEP-" + System.nanoTime();
        AssessmentTest test = new AssessmentTest(code, "Operations Only", AssessmentTest.KIND_T0, 60);
        test.setTargetType(AssessmentTest.TARGET_DEPARTMENT);
        test.setTargetValue("Operations");
        AssessmentTest created = testService.createTest(test, cellOfficer);
        AssessmentTest published = testService.publishTest(created.getId(), cellOfficer);

        assertTrue(testService.isEligible(published, empOperations));
        assertFalse(testService.isEligible(published, empFinance));
    }

    @Test
    void eligibilityByRoleFamily() {
        String code = "TEST-FAM-" + System.nanoTime();
        AssessmentTest test = new AssessmentTest(code, "Station Controllers Only", AssessmentTest.KIND_T0, 60);
        test.setTargetType(AssessmentTest.TARGET_ROLE_FAMILY);
        test.setTargetValue("SC");
        AssessmentTest created = testService.createTest(test, cellOfficer);
        AssessmentTest published = testService.publishTest(created.getId(), cellOfficer);

        assertTrue(testService.isEligible(published, empOperations));
        assertFalse(testService.isEligible(published, empFinance));
    }

    @Test
    void eligibilityByScheduleWindow() {
        String code = "TEST-WIN-" + System.nanoTime();
        AssessmentTest test = new AssessmentTest(code, "Window Test", AssessmentTest.KIND_T0, 60);
        test.setStartAt(LocalDateTime.now().plusHours(2)); // future start
        AssessmentTest created = testService.createTest(test, cellOfficer);
        AssessmentTest published = testService.publishTest(created.getId(), cellOfficer);

        // Not yet open
        assertFalse(testService.isEligible(published, empOperations));

        // Window open
        published.setStartAt(LocalDateTime.now().minusHours(1));
        published.setEndAt(LocalDateTime.now().plusHours(1));
        testRepository.save(published);
        assertTrue(testService.isEligible(published, empOperations));

        // Window expired
        published.setEndAt(LocalDateTime.now().minusMinutes(5));
        testRepository.save(published);
        assertFalse(testService.isEligible(published, empOperations));
    }
}
