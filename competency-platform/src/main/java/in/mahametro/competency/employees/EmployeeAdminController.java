package in.mahametro.competency.employees;

import in.mahametro.competency.access.AccessService;
import in.mahametro.competency.access.CurrentUser;
import in.mahametro.competency.access.EmployeeRole;
import in.mahametro.competency.access.EmployeeRoleRepository;
import in.mahametro.competency.access.Role;
import in.mahametro.competency.access.RoleRepository;
import in.mahametro.competency.core.AuditService;
import java.security.Principal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Staff list (50 per page, searchable) and the screen to give or remove roles. */
@Controller
@RequestMapping("/admin/employees")
public class EmployeeAdminController {

    private static final int PAGE_SIZE = 50;

    private final EmployeeRepository employees;
    private final EmployeeRoleRepository employeeRoles;
    private final RoleRepository roles;
    private final CurrentUser currentUser;
    private final AccessService access;
    private final AuditService audit;

    public EmployeeAdminController(EmployeeRepository employees, EmployeeRoleRepository employeeRoles,
            RoleRepository roles, CurrentUser currentUser, AccessService access, AuditService audit) {
        this.employees = employees;
        this.employeeRoles = employeeRoles;
        this.roles = roles;
        this.currentUser = currentUser;
        this.access = access;
        this.audit = audit;
    }

    @GetMapping
    public String list(@RequestParam(name = "q", defaultValue = "") String q,
            @RequestParam(name = "page", defaultValue = "0") int page, Principal principal, Model model) {
        currentUser.require(principal, "employee.manage");
        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by("empId"));
        String term = q.trim();
        Page<Employee> result = term.isEmpty()
                ? employees.findAll(pageable)
                : employees.findByEmpIdContainingIgnoreCaseOrNameEnContainingIgnoreCase(term, term, pageable);
        model.addAttribute("result", result);
        model.addAttribute("q", term);
        return "admin/employees";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Long id, Principal principal, Model model) {
        currentUser.require(principal, "employee.manage");
        Employee employee = employees.findById(id).orElseThrow(() -> new AccessDeniedException("No such employee"));
        model.addAttribute("employee", employee);
        model.addAttribute("grants", employeeRoles.findByEmployeeId(id));
        model.addAttribute("roles", roles.findAll(Sort.by("code")));
        return "admin/employee";
    }

    @PostMapping("/{id}/roles")
    public String grant(@PathVariable("id") Long id, @RequestParam("roleCode") String roleCode,
            @RequestParam(name = "scopeType", defaultValue = "ALL") String scopeType,
            @RequestParam(name = "scopeValue", defaultValue = "") String scopeValue, Principal principal) {
        Employee actor = currentUser.require(principal, "access.manage");
        Employee employee = employees.findById(id).orElseThrow(() -> new AccessDeniedException("No such employee"));
        if (Role.SUPERADMIN.equals(roleCode) && !isSuperadmin(actor)) {
            throw new AccessDeniedException("Only a super administrator can give that role");
        }
        String value = EmployeeRole.SCOPE_ALL.equals(scopeType) ? "" : scopeValue.trim();
        if (roles.existsById(roleCode)
                && !employeeRoles.existsByEmployeeIdAndRoleCodeAndScopeTypeAndScopeValue(id, roleCode, scopeType, value)) {
            employeeRoles.save(new EmployeeRole(id, roleCode, scopeType, value, actor.getEmpId()));
            audit.record(actor.getEmpId(), "role_granted", "employee", employee.getEmpId(),
                    roleCode + " " + scopeType + " " + value);
        }
        return "redirect:/admin/employees/" + id;
    }

    @PostMapping("/{id}/roles/{grantId}/delete")
    public String revoke(@PathVariable("id") Long id, @PathVariable("grantId") Long grantId, Principal principal) {
        Employee actor = currentUser.require(principal, "access.manage");
        Employee employee = employees.findById(id).orElseThrow(() -> new AccessDeniedException("No such employee"));
        employeeRoles.findById(grantId).ifPresent(grant -> {
            if (!grant.getEmployeeId().equals(id)) {
                return;
            }
            if (Role.SUPERADMIN.equals(grant.getRoleCode()) && !isSuperadmin(actor)) {
                throw new AccessDeniedException("Only a super administrator can remove that role");
            }
            employeeRoles.delete(grant);
            audit.record(actor.getEmpId(), "role_removed", "employee", employee.getEmpId(),
                    grant.getRoleCode() + " " + grant.getScopeType() + " " + grant.getScopeValue());
        });
        return "redirect:/admin/employees/" + id;
    }

    private boolean isSuperadmin(Employee employee) {
        for (EmployeeRole grant : employeeRoles.findByEmployeeId(employee.getId())) {
            if (Role.SUPERADMIN.equals(grant.getRoleCode())) {
                return true;
            }
        }
        return false;
    }
}
