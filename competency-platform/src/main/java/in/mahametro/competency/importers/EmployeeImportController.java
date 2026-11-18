package in.mahametro.competency.importers;

import in.mahametro.competency.access.CurrentUser;
import in.mahametro.competency.core.AuditService;
import in.mahametro.competency.employees.DesignationMap;
import in.mahametro.competency.employees.DesignationMapRepository;
import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.employees.EmployeeRepository;
import in.mahametro.competency.employees.GradeRepository;
import in.mahametro.competency.employees.RoleFamilyRepository;
import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller for employee bulk CSV/Excel import and designation-to-framework mapping.
 */
@Controller
public class EmployeeImportController {

    private final EmployeeImportService importService;
    private final DesignationMapRepository designationMaps;
    private final RoleFamilyRepository roleFamilies;
    private final GradeRepository grades;
    private final EmployeeRepository employees;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public EmployeeImportController(EmployeeImportService importService, DesignationMapRepository designationMaps,
            RoleFamilyRepository roleFamilies, GradeRepository grades, EmployeeRepository employees,
            CurrentUser currentUser, AuditService audit) {
        this.importService = importService;
        this.designationMaps = designationMaps;
        this.roleFamilies = roleFamilies;
        this.grades = grades;
        this.employees = employees;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @GetMapping("/admin/employees/import")
    public String importPage(Principal principal, Model model) {
        currentUser.require(principal, "employee.manage");
        return "admin/employee_import";
    }

    @PostMapping("/admin/employees/import")
    public String handleImport(@RequestParam("file") MultipartFile file,
            @RequestParam(name = "dryRun", defaultValue = "false") boolean dryRun,
            Principal principal, RedirectAttributes redirectAttributes) {
        Employee actor = currentUser.require(principal, "employee.manage");

        if (file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please select a valid CSV or Excel file to upload.");
            return "redirect:/admin/employees/import";
        }

        try {
            EmployeeImportReport report = importService.importEmployees(
                    file.getInputStream(), file.getOriginalFilename(), actor, dryRun);
            redirectAttributes.addFlashAttribute("report", report);
            if (report.getErrors().isEmpty()) {
                redirectAttributes.addFlashAttribute("successMessage",
                        (dryRun ? "Dry-run validation complete. " : "Employee import completed successfully. ")
                        + "Created: " + report.getCreated() + ", Updated: " + report.getUpdated() + ", Skipped: " + report.getSkipped());
            } else {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Import encountered " + report.getErrors().size() + " error(s). Please review the report details below.");
            }
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to process import file: " + ex.getMessage());
        }

        return "redirect:/admin/employees/import";
    }

    @GetMapping("/admin/employees/template")
    public ResponseEntity<byte[]> downloadTemplate(Principal principal) throws IOException {
        currentUser.require(principal, "employee.manage");
        byte[] bytes = importService.generateTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"employee_master_template.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @GetMapping("/admin/designations")
    public String listDesignations(Principal principal, Model model) {
        currentUser.require(principal, "employee.manage");
        model.addAttribute("mappings", designationMaps.findAllByOrderByDesignationAsc());
        model.addAttribute("families", roleFamilies.findAll(Sort.by("code")));
        model.addAttribute("grades", grades.findAll(Sort.by("code")));
        return "admin/designations";
    }

    @PostMapping("/admin/designations")
    public String saveDesignation(@RequestParam("designation") String designation,
            @RequestParam(name = "roleFamilyCode", defaultValue = "") String roleFamilyCode,
            @RequestParam(name = "gradeCode", defaultValue = "") String gradeCode,
            @RequestParam(name = "applyToExisting", defaultValue = "true") boolean applyToExisting,
            Principal principal, RedirectAttributes redirectAttributes) {
        Employee actor = currentUser.require(principal, "employee.manage");
        String desig = designation.trim();
        if (desig.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Designation title cannot be empty.");
            return "redirect:/admin/designations";
        }

        Optional<DesignationMap> existingOpt = designationMaps.findByDesignationIgnoreCase(desig);
        DesignationMap map = existingOpt.orElseGet(() -> new DesignationMap(desig, null, null));
        map.setRoleFamilyCode(roleFamilyCode);
        map.setGradeCode(gradeCode);
        designationMaps.save(map);

        int updatedCount = 0;
        if (applyToExisting) {
            List<Employee> matching = employees.findByDesignationIgnoreCase(desig);
            for (Employee e : matching) {
                boolean changed = false;
                if ((e.getRoleFamilyCode() == null || e.getRoleFamilyCode().isBlank()) && map.getRoleFamilyCode() != null) {
                    e.setRoleFamilyCode(map.getRoleFamilyCode());
                    changed = true;
                }
                if ((e.getGradeCode() == null || e.getGradeCode().isBlank()) && map.getGradeCode() != null) {
                    e.setGradeCode(map.getGradeCode());
                    changed = true;
                }
                if (changed) {
                    employees.save(e);
                    updatedCount++;
                }
            }
        }

        audit.record(actor.getEmpId(), "designation_mapped", "designation_map", desig,
                "family=" + roleFamilyCode + ", grade=" + gradeCode + ", applied=" + updatedCount);

        redirectAttributes.addFlashAttribute("successMessage",
                "Saved mapping for '" + desig + "'." + (updatedCount > 0 ? " Applied to " + updatedCount + " employee(s)." : ""));
        return "redirect:/admin/designations";
    }

    @PostMapping("/admin/designations/{id}/delete")
    public String deleteDesignation(@PathVariable("id") Long id, Principal principal,
            RedirectAttributes redirectAttributes) {
        Employee actor = currentUser.require(principal, "employee.manage");
        designationMaps.findById(id).ifPresent(d -> {
            designationMaps.delete(d);
            audit.record(actor.getEmpId(), "designation_unmapped", "designation_map", d.getDesignation(), "");
            redirectAttributes.addFlashAttribute("successMessage", "Deleted mapping for '" + d.getDesignation() + "'.");
        });
        return "redirect:/admin/designations";
    }
}
