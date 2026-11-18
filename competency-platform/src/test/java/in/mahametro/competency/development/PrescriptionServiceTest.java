package in.mahametro.competency.development;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.employees.EmployeeRepository;
import in.mahametro.competency.scoring.ProfileRow;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PrescriptionServiceTest {

    @Autowired
    PrescriptionService prescriptionService;

    @Autowired
    InterventionRepository interventionRepository;

    @Autowired
    DevelopmentPlanRepository planRepository;

    @Autowired
    DevelopmentPlanItemRepository itemRepository;

    @Autowired
    EmployeeRepository employeeRepository;

    private Employee employee;
    private Employee supervisor;

    @BeforeEach
    void setUp() {
        employee = employeeRepository.findByEmpId("TEST-EMP1").orElse(null);
        if (employee == null) {
            Employee emp = new Employee("TEST-EMP1", "Test Candidate");
            emp.setDesignation("Station Controller");
            emp.setDepartment("Operations");
            emp.setRoleFamilyCode("SC");
            emp.setGradeCode("G1");
            employee = employeeRepository.save(emp);
        }
        supervisor = employeeRepository.findByEmpId("TEST-SUP").orElse(null);
        if (supervisor == null) {
            Employee sup = new Employee("TEST-SUP", "Test Supervisor");
            sup.setDesignation("Station Manager");
            sup.setDepartment("Operations");
            sup.setRoleFamilyCode("SC");
            sup.setGradeCode("G3");
            supervisor = employeeRepository.save(sup);
        }
    }

    @Test
    void testGapTypeClassification() {
        // ProfileRow(code, name, category, safetyCritical, required, score, level)
        // 1. Safety critical gap: required 3, level 1 (gap = 2)
        ProfileRow safetyRow = new ProfileRow("C01", "Traction Safety", "Domain", true, 3, 35.0, 1);
        assertEquals("safety", prescriptionService.gapType(safetyRow));

        // 2. Behavioural gap: required 3, level 2 (gap = 1)
        ProfileRow behavRow = new ProfileRow("B01", "Communication", "Behavioural", false, 3, 45.0, 2);
        assertEquals("behavioural", prescriptionService.gapType(behavRow));

        // 3. Major gap: required 4, level 1 (gap = 3)
        ProfileRow majorRow = new ProfileRow("C02", "Brake Overhaul", "Domain", false, 4, 30.0, 1);
        assertEquals("major", prescriptionService.gapType(majorRow));

        // 4. Minor gap: required 3, level 2 (gap = 1)
        ProfileRow minorRow = new ProfileRow("C03", "Air Conditioning", "Domain", false, 3, 45.0, 2);
        assertEquals("minor", prescriptionService.gapType(minorRow));

        // 5. Strength (gap <= 0 and level >= 4): required 3, level 4 (gap = 0)
        ProfileRow strengthRow = new ProfileRow("C04", "PLC Programming", "Domain", false, 3, 80.0, 4);
        assertEquals("strength", prescriptionService.gapType(strengthRow));

        // 6. Meets standard (level < 4 and gap <= 0): required 2, level 2 (gap = 0)
        ProfileRow meetsRow = new ProfileRow("C05", "Basic Tools", "Core", false, 2, 50.0, 2);
        assertNull(prescriptionService.gapType(meetsRow));

        // 7. Unassessed (score == null)
        ProfileRow unassessedRow = new ProfileRow("C06", "Advanced Signaling", "Domain", false, 3, null, 0);
        assertNull(prescriptionService.gapType(unassessedRow));
    }

    @Test
    void testInterventionMatching() {
        // Test seeded catalogue matching
        // C01 has specific safety intervention seeded in V13
        ProfileRow c01 = new ProfileRow("C01", "Safety", "Core", true, 3, 35.0, 1);
        Intervention ivC01 = prescriptionService.pickIntervention(c01, "safety");
        assertNotNull(ivC01);
        assertEquals("C01", ivC01.getCompetencyCode());

        // Generic fallback for minor gap
        ProfileRow c99 = new ProfileRow("C99", "Some Topic", "Domain", false, 3, 45.0, 2);
        Intervention genericMinor = prescriptionService.pickIntervention(c99, "minor");
        assertNotNull(genericMinor);
        assertEquals("minor", genericMinor.getGapType());
    }

    @Test
    void testPlanGenerationAndConfirmation() {
        // Generate plan for DEMO-EMP1
        DevelopmentPlan plan = prescriptionService.generatePlan(employee, "baseline");
        assertNotNull(plan);
        assertNotNull(plan.getId());
        assertEquals("proposed", plan.getStatus());
        assertEquals("baseline", plan.getCycle());
        assertEquals(employee.getId(), plan.getEmployeeId());

        List<DevelopmentPlanItem> items = prescriptionService.getItems(plan.getId());
        assertNotNull(items);

        if (!items.isEmpty()) {
            DevelopmentPlanItem first = items.get(0);
            boolean hasSafety = items.stream().anyMatch(it -> "safety".equalsIgnoreCase(it.getGapType()));
            if (hasSafety) {
                assertEquals("safety", first.getGapType());
                assertEquals(1, first.getPriority());
                assertNotNull(first.getTargetDate());
                assertTrue(first.getTargetDate().isAfter(LocalDateTime.now().plusDays(25)));
            }
        }

        // Supervisor confirms the plan
        DevelopmentPlan confirmed = prescriptionService.confirmPlan(plan.getId(), supervisor, "Agreed during supervisory check");
        assertEquals("confirmed", confirmed.getStatus());
        assertEquals(supervisor.getEmpId(), confirmed.getConfirmedBy());
        assertNotNull(confirmed.getConfirmedAt());
    }
}
