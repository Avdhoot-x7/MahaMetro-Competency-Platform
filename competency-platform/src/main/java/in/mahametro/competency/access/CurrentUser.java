package in.mahametro.competency.access;

import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.employees.EmployeeRepository;
import java.security.Principal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Finds the signed-in employee and checks a permission. Controllers call require(...) first. */
@Component
public class CurrentUser {

    private final EmployeeRepository employees;
    private final AccessService access;

    public CurrentUser(EmployeeRepository employees, AccessService access) {
        this.employees = employees;
        this.access = access;
    }

    public Employee get(Principal principal) {
        return employees.findByEmpId(principal.getName())
                .orElseThrow(() -> new AccessDeniedException("Unknown user"));
    }

    /** Lets the employee in if they hold any one of the permissions, in any scope. */
    public Employee requireAny(Principal principal, String... permissions) {
        Employee employee = get(principal);
        for (String permission : permissions) {
            if (access.hasAny(employee.getId(), permission)) {
                return employee;
            }
        }
        throw new AccessDeniedException("You do not have access to this page");
    }

    public Employee require(Principal principal, String permission) {
        Employee employee = get(principal);
        if (!access.can(employee.getId(), permission)) {
            throw new AccessDeniedException("You do not have access to this page");
        }
        return employee;
    }
}
