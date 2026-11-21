package in.mahametro.competency.reporting;

import in.mahametro.competency.access.CurrentUser;
import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.employees.EmployeeRepository;
import java.io.IOException;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller serving Supervisor Team dashboard, HoD Department dashboard,
 * Executive Organisation scorecard, and Excel exports.
 */
@Controller
public class DashboardController {

    private final ReportingService reportingService;
    private final EmployeeRepository employees;
    private final CurrentUser currentUser;

    public DashboardController(ReportingService reportingService, EmployeeRepository employees, CurrentUser currentUser) {
        this.reportingService = reportingService;
        this.employees = employees;
        this.currentUser = currentUser;
    }

    @GetMapping("/team")
    public String supervisorTeam(Principal principal, Model model) {
        Employee user = currentUser.get(principal);
        List<Employee> members = employees.findByReportingOfficerAndActiveTrue(user.getEmpId());
        DashboardSummary summary;

        if (!members.isEmpty()) {
            summary = reportingService.getSupervisorDashboard(user);
        } else {
            // For individual staff (e.g. Train Operators), load peer team under same supervisor or department colleagues
            if (user.getReportingOfficer() != null && !user.getReportingOfficer().isBlank()) {
                members = employees.findByReportingOfficerAndActiveTrue(user.getReportingOfficer());
            }
            if (members.isEmpty() && user.getDepartment() != null) {
                members = employees.findByDepartmentAndActiveTrue(user.getDepartment());
            }
            summary = reportingService.buildDashboardForEmployees(members);
        }

        StaffFilter filter = new StaffFilter();
        filter.setReportingOfficer(user.getEmpId());

        model.addAttribute("user", user);
        model.addAttribute("summary", summary);
        model.addAttribute("members", members);
        return "reporting/team";
    }

    @GetMapping("/dashboards/department")
    public String departmentDashboard(@RequestParam(name = "dept", defaultValue = "") String dept,
            Principal principal, Model model) {
        Employee user = currentUser.get(principal);

        List<String> depts = employees.findDistinctDepartments();
        String selectedDept = dept.trim();
        if (selectedDept.isEmpty()) {
            selectedDept = (user.getDepartment() != null && !user.getDepartment().isBlank())
                    ? user.getDepartment()
                    : (!depts.isEmpty() ? depts.get(0) : "");
        }

        DashboardSummary summary = reportingService.getDepartmentDashboard(selectedDept);

        model.addAttribute("user", user);
        model.addAttribute("dept", selectedDept);
        model.addAttribute("depts", depts);
        model.addAttribute("summary", summary);
        return "reporting/department";
    }

    @GetMapping("/dashboards/org")
    public String orgDashboard(Principal principal, Model model) {
        Employee user = currentUser.get(principal);
        DashboardSummary summary = reportingService.getOrgDashboard();

        model.addAttribute("user", user);
        model.addAttribute("summary", summary);
        return "reporting/org";
    }

    @GetMapping("/results/export")
    public ResponseEntity<byte[]> exportResults(@ModelAttribute StaffFilter filter, Principal principal) throws IOException {
        Employee user = currentUser.get(principal);
        byte[] bytes = reportingService.exportStaffResults(user, filter);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"staff_competency_results.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
}
