package in.mahametro.competency.auth;

import in.mahametro.competency.core.AuditService;
import in.mahametro.competency.employees.EmployeeRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Locks an account for a while after several wrong passwords in a row. */
@Service
public class LoginAttemptService {

    public static final int MAX_FAILURES = 5;
    public static final int LOCK_MINUTES = 15;

    private final EmployeeRepository employees;
    private final AuditService audit;

    public LoginAttemptService(EmployeeRepository employees, AuditService audit) {
        this.employees = employees;
        this.audit = audit;
    }

    @Transactional
    public void failed(String empId) {
        employees.findByEmpId(empId).ifPresent(employee -> {
            int failures = employee.getFailedLogins() + 1;
            if (failures >= MAX_FAILURES) {
                employee.setFailedLogins(0);
                employee.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
                audit.record(empId, "account_locked", "employee", empId, MAX_FAILURES + " wrong passwords");
            } else {
                employee.setFailedLogins(failures);
            }
            employees.save(employee);
        });
    }

    @Transactional
    public void succeeded(String empId) {
        employees.findByEmpId(empId).ifPresent(employee -> {
            employee.setFailedLogins(0);
            employee.setLockedUntil(null);
            employee.setLastLogin(LocalDateTime.now());
            employees.save(employee);
        });
    }
}
