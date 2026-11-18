package in.mahametro.competency.demo;

import in.mahametro.competency.access.EmployeeRole;
import in.mahametro.competency.access.EmployeeRoleRepository;
import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.employees.EmployeeRepository;
import in.mahametro.competency.framework.RoleProfile;
import in.mahametro.competency.framework.RoleProfileRepository;
import in.mahametro.competency.questions.DemoQuestions;
import in.mahametro.competency.scoring.CompetencyScore;
import in.mahametro.competency.scoring.CompetencyScoreRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Development only. When app.demo-data=true and the demo accounts do not exist yet, creates 10 made-up
 * employees (one per role), made-up baseline scores and three sample questions. See DEMO-ACCOUNTS.md.
 * Never set app.demo-data in production.
 */
@Component
public class DemoDataLoader implements ApplicationRunner {

    public static final String PASSWORD = "Demo@2027";

    private final EmployeeRepository employees;
    private final EmployeeRoleRepository employeeRoles;
    private final RoleProfileRepository roleProfiles;
    private final CompetencyScoreRepository scores;
    private final DemoQuestions demoQuestions;
    private final PasswordEncoder passwordEncoder;
    private final in.mahametro.competency.reporting.ReportingService reportingService;
    private final boolean enabled;

    public DemoDataLoader(EmployeeRepository employees, EmployeeRoleRepository employeeRoles,
            RoleProfileRepository roleProfiles, CompetencyScoreRepository scores, DemoQuestions demoQuestions,
            PasswordEncoder passwordEncoder,
            in.mahametro.competency.reporting.ReportingService reportingService,
            @Value("${app.demo-data:false}") boolean enabled) {
        this.employees = employees;
        this.employeeRoles = employeeRoles;
        this.roleProfiles = roleProfiles;
        this.scores = scores;
        this.demoQuestions = demoQuestions;
        this.passwordEncoder = passwordEncoder;
        this.reportingService = reportingService;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled || employees.findByEmpId("DEMO-CELL").isPresent()) {
            return;
        }
        String hash = passwordEncoder.encode(PASSWORD);
        //            id            name               designation                 department    family  grade reports-to  role             scope         value
        add(hash, "DEMO-EMP1", "Asha Kulkarni", "Station Controller", "Operations", "SC", "G1", "DEMO-SUP", null, null, null);
        add(hash, "DEMO-EMP2", "Rohan Patil", "Train Operator", "Operations", "TO", "G2", "DEMO-SUP", null, null, null);
        add(hash, "DEMO-EMP3", "Meera Joshi", "Accounts Assistant", "Finance", "FA", "G1", "", null, null, null);
        add(hash, "DEMO-SUP", "Sunil Deshmukh", "Station Manager", "Operations", "SC", "G3", "DEMO-HOD", "supervisor", "ALL", "");
        add(hash, "DEMO-HOD", "Kavita Rao", "Head of Operations", "Operations", "OCC", "G5", "", "hod", "DEPARTMENT", "Operations");
        add(hash, "DEMO-MGT", "Anil Mehta", "Director", "Corporate", "CORP", "G5", "", "management", "ALL", "");
        Employee programmer = add(hash, "DEMO-PROG", "Neha Shah", "Question Programmer", "IT", "IT", "G3", "", "programmer", "ALL", "");
        Employee expert = add(hash, "DEMO-SME", "Vikram Iyer", "Subject Expert", "Safety", "SFS", "G4", "", "subject_expert", "ALL", "");
        add(hash, "DEMO-PROC", "Pooja Nair", "Proctor", "HR", "HRA", "G2", "", "proctor", "ALL", "");
        add(hash, "DEMO-CELL", "Rahul Gokhale", "Assessment Cell Officer", "HR", "HRA", "G4", "", "cell", "ALL", "");
        demoQuestions.create(programmer, expert);
    }

    private Employee add(String hash, String empId, String name, String designation, String department,
            String family, String grade, String reportsTo, String role, String scopeType, String scopeValue) {
        Employee employee = new Employee(empId, name);
        employee.setDesignation(designation);
        employee.setDepartment(department);
        employee.setRoleFamilyCode(family);
        employee.setGradeCode(grade);
        employee.setReportingOfficer(reportsTo);
        employee.setEmail(empId.toLowerCase() + "@demo.example");
        employee.setPasswordHash(hash);
        employee.setSource("demo");
        employee = employees.save(employee);
        if (role != null) {
            employeeRoles.save(new EmployeeRole(employee.getId(), role, scopeType, scopeValue, "demo"));
        }
        // made-up baseline (T0) knowledge scores between 35 and 94, the same on every run
        for (RoleProfile need : roleProfiles.findByRoleFamilyCode(family)) {
            double score = 35 + Math.floorMod((empId + need.getCompetencyCode()).hashCode(), 60);
            scores.save(new CompetencyScore(employee.getId(), need.getCompetencyCode(), "T0", "knowledge", score,
                    "demo"));
        }
        reportingService.refreshSummary(employee.getId());
        return employee;
    }
}

