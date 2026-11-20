package in.mahametro.competency.employees;

import in.mahametro.competency.access.CurrentUser;
import in.mahametro.competency.access.EmployeeRole;
import in.mahametro.competency.access.EmployeeRoleRepository;
import in.mahametro.competency.core.AuditService;
import in.mahametro.competency.reporting.ProfileSummary;
import in.mahametro.competency.reporting.ProfileSummaryRepository;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller displaying rich employee profile details and personal metadata.
 * Allows staff to review personal metadata, supervisor hierarchy, system access,
 * and update their contact details and preferred language.
 */
@Controller
public class ProfileController {

    private final EmployeeRepository employees;
    private final CurrentUser currentUser;
    private final EmployeeRoleRepository employeeRoles;
    private final RoleFamilyRepository roleFamilies;
    private final ProfileSummaryRepository profileSummaries;
    private final AuditService audit;

    public ProfileController(EmployeeRepository employees, CurrentUser currentUser,
            EmployeeRoleRepository employeeRoles, RoleFamilyRepository roleFamilies,
            ProfileSummaryRepository profileSummaries, AuditService audit) {
        this.employees = employees;
        this.currentUser = currentUser;
        this.employeeRoles = employeeRoles;
        this.roleFamilies = roleFamilies;
        this.profileSummaries = profileSummaries;
        this.audit = audit;
    }

    @GetMapping("/profile")
    public String myProfile(Principal principal, Model model) {
        Employee me = currentUser.get(principal);
        populateModel(me, true, model);
        return "employees/profile";
    }

    @GetMapping("/profile/{id}")
    public String viewProfile(@PathVariable("id") Long id, Principal principal, Model model) {
        Employee me = currentUser.get(principal);
        Employee target = employees.findById(id).orElseThrow(() -> new AccessDeniedException("No such employee"));
        boolean own = me.getId().equals(target.getId());
        populateModel(target, own, model);
        return "employees/profile";
    }

    @PostMapping("/profile/update")
    public String updateMetadata(
            @RequestParam("nameMr") String nameMr,
            @RequestParam("mobile") String mobile,
            @RequestParam("email") String email,
            @RequestParam("location") String location,
            @RequestParam("preferredLang") String preferredLang,
            Principal principal) {
        Employee me = currentUser.get(principal);
        me.setNameMr(nameMr != null ? nameMr.trim() : "");
        me.setMobile(mobile != null ? mobile.trim() : "");
        me.setEmail(email != null ? email.trim() : "");
        me.setLocation(location != null ? location.trim() : "");
        me.setPreferredLang("mr".equalsIgnoreCase(preferredLang) ? "mr" : "en");
        employees.save(me);
        audit.record(me.getEmpId(), "profile_updated", "employee", me.getEmpId(), "Updated personal metadata");
        return "redirect:/profile?updated=true";
    }

    private void populateModel(Employee emp, boolean own, Model model) {
        model.addAttribute("employee", emp);
        model.addAttribute("own", own);

        Optional<Employee> supervisor = (emp.getReportingOfficer() != null && !emp.getReportingOfficer().isBlank())
                ? employees.findByEmpId(emp.getReportingOfficer())
                : Optional.empty();
        model.addAttribute("supervisor", supervisor.orElse(null));

        Optional<RoleFamily> family = (emp.getRoleFamilyCode() != null && !emp.getRoleFamilyCode().isBlank())
                ? roleFamilies.findById(emp.getRoleFamilyCode())
                : Optional.empty();
        model.addAttribute("roleFamily", family.orElse(null));

        Optional<ProfileSummary> summary = profileSummaries.findById(emp.getId());
        model.addAttribute("summary", summary.orElse(null));

        List<EmployeeRole> grants = employeeRoles.findByEmployeeId(emp.getId());
        model.addAttribute("grants", grants);
    }
}
