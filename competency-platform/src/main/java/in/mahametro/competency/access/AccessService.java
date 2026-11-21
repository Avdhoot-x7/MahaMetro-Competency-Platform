package in.mahametro.competency.access;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The one place that answers "may this employee do this, here?". */
@Service
public class AccessService {

    private final EmployeeRoleRepository employeeRoles;
    private final RoleRepository roles;

    public AccessService(EmployeeRoleRepository employeeRoles, RoleRepository roles) {
        this.employeeRoles = employeeRoles;
        this.roles = roles;
    }

    /** True if the employee holds the permission everywhere. */
    @Transactional(readOnly = true)
    public boolean can(Long employeeId, String permission) {
        return can(employeeId, permission, EmployeeRole.SCOPE_ALL, "");
    }

    /** True if the employee holds the permission in at least one scope. Used to decide who may open a page. */
    @Transactional(readOnly = true)
    public boolean hasAny(Long employeeId, String permission) {
        for (EmployeeRole grant : employeeRoles.findByEmployeeId(employeeId)) {
            if (Role.SUPERADMIN.equals(grant.getRoleCode())) {
                return true;
            }
            Role role = roles.findById(grant.getRoleCode()).orElse(null);
            if (role != null && role.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    /** True if the employee holds any one of the specified permissions in at least one scope. */
    @Transactional(readOnly = true)
    public boolean hasAny(Long employeeId, String... permissions) {
        for (String p : permissions) {
            if (hasAny(employeeId, p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if the employee holds the permission for the given scope, for example
     * can(id, "question.review", "COMPETENCY", "D07"). A role granted with scope ALL covers every scope.
     */
    @Transactional(readOnly = true)
    public boolean can(Long employeeId, String permission, String scopeType, String scopeValue) {
        for (EmployeeRole grant : employeeRoles.findByEmployeeId(employeeId)) {
            if (Role.SUPERADMIN.equals(grant.getRoleCode())) {
                return true;
            }
            Role role = roles.findById(grant.getRoleCode()).orElse(null);
            if (role == null || !role.hasPermission(permission)) {
                continue;
            }
            if (EmployeeRole.SCOPE_ALL.equals(grant.getScopeType())) {
                return true;
            }
            if (grant.getScopeType().equals(scopeType) && grant.getScopeValue().equals(scopeValue)) {
                return true;
            }
        }
        return false;
    }
}
