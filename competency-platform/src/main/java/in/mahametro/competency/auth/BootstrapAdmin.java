package in.mahametro.competency.auth;

import in.mahametro.competency.access.EmployeeRole;
import in.mahametro.competency.access.EmployeeRoleRepository;
import in.mahametro.competency.access.Role;
import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.employees.EmployeeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first super administrator when app.bootstrap-admin.emp-id and .password are set and that
 * employee does not exist yet. Set in application-dev.yml; in production set them once, then remove them.
 */
@Component
public class BootstrapAdmin implements ApplicationRunner {

    private final EmployeeRepository employees;
    private final EmployeeRoleRepository employeeRoles;
    private final PasswordEncoder passwordEncoder;
    private final String empId;
    private final String password;

    public BootstrapAdmin(EmployeeRepository employees, EmployeeRoleRepository employeeRoles,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap-admin.emp-id:}") String empId,
            @Value("${app.bootstrap-admin.password:}") String password) {
        this.employees = employees;
        this.employeeRoles = employeeRoles;
        this.passwordEncoder = passwordEncoder;
        this.empId = empId;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (empId.isBlank() || password.isBlank() || employees.findByEmpId(empId).isPresent()) {
            return;
        }
        Employee admin = new Employee(empId, "System Administrator");
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setSource("bootstrap");
        admin = employees.save(admin);
        employeeRoles.save(new EmployeeRole(admin.getId(), Role.SUPERADMIN, EmployeeRole.SCOPE_ALL, "", "system"));
    }
}
