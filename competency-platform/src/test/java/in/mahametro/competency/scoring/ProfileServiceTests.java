package in.mahametro.competency.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.employees.EmployeeRepository;
import in.mahametro.competency.framework.RoleProfile;
import in.mahametro.competency.framework.RoleProfileRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ProfileServiceTests {

    @Autowired EmployeeRepository employees;
    @Autowired RoleProfileRepository roleProfiles;
    @Autowired CompetencyScoreRepository scores;
    @Autowired ProfileService profiles;

    private Employee stationStaff(String empId, double safetyScore) {
        Employee employee = new Employee(empId, empId);
        employee.setRoleFamilyCode("SC");
        employee.setGradeCode("G2");
        employee = employees.save(employee);
        for (RoleProfile need : roleProfiles.findByRoleFamilyCode("SC")) {
            double score = "C01".equals(need.getCompetencyCode()) ? safetyScore : 95;
            scores.save(new CompetencyScore(employee.getId(), need.getCompetencyCode(), "T0", "knowledge", score, "test"));
        }
        return employee;
    }

    @Test
    void safetyGapMakesThePersonRed() {
        Employee strong = stationStaff("S-GREEN", 95);
        Employee weakOnSafety = stationStaff("S-RED", 60);
        Employee notTested = employees.save(new Employee("S-NONE", "S-NONE"));

        Profile green = profiles.build(strong);
        assertEquals("Green", green.getReadiness());
        assertEquals(100.0, green.getIndex());
        assertEquals(21, green.getRows().size());

        // Station Operations needs level 4 in C01 (safety-critical); 60% is level 3
        Profile red = profiles.build(weakOnSafety);
        assertEquals("Red", red.getReadiness());
        assertEquals(1, red.getSafetyGates());
        assertEquals(1, red.getGaps());

        assertEquals("Not assessed", profiles.build(notTested).getReadiness());
    }

    @Test
    void missingMethodsShareIsSpreadOverTheRest() {
        Map<String, Integer> weights = Map.of("knowledge", 35, "sjt", 20, "practical", 35, "rating", 10);
        assertEquals(80.0, ProfileService.composite(Map.of("knowledge", 80.0), weights));
        assertEquals(60.0, ProfileService.composite(Map.of("knowledge", 80.0, "practical", 40.0), weights));
    }
}
