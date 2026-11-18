package in.mahametro.competency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import in.mahametro.competency.access.AccessService;
import in.mahametro.competency.access.EmployeeRole;
import in.mahametro.competency.access.EmployeeRoleRepository;
import in.mahametro.competency.access.RoleRepository;
import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.employees.EmployeeRepository;
import in.mahametro.competency.employees.GradeRepository;
import in.mahametro.competency.employees.RoleFamilyRepository;
import in.mahametro.competency.framework.CompetencyRepository;
import in.mahametro.competency.framework.CutScoreRepository;
import in.mahametro.competency.framework.RoleProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Runs every migration on an in-memory database and checks the framework data and the access rules. */
@SpringBootTest
@ActiveProfiles("test")
class SeedDataTests {

    @Autowired RoleFamilyRepository roleFamilies;
    @Autowired GradeRepository grades;
    @Autowired CompetencyRepository competencies;
    @Autowired RoleProfileRepository roleProfiles;
    @Autowired CutScoreRepository cutScores;
    @Autowired RoleRepository roles;
    @Autowired EmployeeRepository employees;
    @Autowired EmployeeRoleRepository employeeRoles;
    @Autowired AccessService access;

    @Test
    void frameworkIsLoaded() {
        assertEquals(16, roleFamilies.count());
        assertEquals(5, grades.count());
        assertEquals(44, competencies.count());
        assertEquals(345, roleProfiles.count());
        assertEquals(5, cutScores.findAllByOrderByLevelNoAsc().size());
        assertEquals(9, roles.count());
    }

    @Test
    void scopedRoleOnlyWorksInsideItsScope() {
        Employee reviewer = employees.save(new Employee("T-1001", "Test Reviewer"));
        employeeRoles.save(new EmployeeRole(reviewer.getId(), "subject_expert", "COMPETENCY", "D07", "test"));

        assertTrue(access.can(reviewer.getId(), "question.approve", "COMPETENCY", "D07"));
        assertFalse(access.can(reviewer.getId(), "question.approve", "COMPETENCY", "D08"));
        assertFalse(access.can(reviewer.getId(), "question.write", "COMPETENCY", "D07"));
        assertFalse(access.can(reviewer.getId(), "question.approve"));
    }
}
