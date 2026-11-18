package in.mahametro.competency.auth;

import in.mahametro.competency.access.EmployeeRole;
import in.mahametro.competency.access.EmployeeRoleRepository;
import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.employees.EmployeeRepository;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads an employee for login. The login name is the employee ID; roles become ROLE_SUPERVISOR and so on. */
@Service
public class EmployeeUserDetailsService implements UserDetailsService {

    private final EmployeeRepository employees;
    private final EmployeeRoleRepository employeeRoles;

    public EmployeeUserDetailsService(EmployeeRepository employees, EmployeeRoleRepository employeeRoles) {
        this.employees = employees;
        this.employeeRoles = employeeRoles;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String empId) throws UsernameNotFoundException {
        Employee employee = employees.findByEmpId(empId.trim())
                .orElseThrow(() -> new UsernameNotFoundException("Unknown employee ID"));
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
        for (EmployeeRole grant : employeeRoles.findByEmployeeId(employee.getId())) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + grant.getRoleCode().toUpperCase()));
        }
        String hash = employee.getPasswordHash();
        return User.withUsername(employee.getEmpId())
                .password(hash == null || hash.isEmpty() ? "!no-password-set" : hash)
                .authorities(authorities)
                .disabled(!employee.isActive())
                .accountLocked(employee.isLocked())
                .build();
    }
}
