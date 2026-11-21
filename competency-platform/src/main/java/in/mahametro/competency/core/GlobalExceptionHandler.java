package in.mahametro.competency.core;

import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.employees.EmployeeRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.security.Principal;

/**
 * Global Exception Handler preventing unhandled stack trace crashes.
 * Gracefully converts access denials and runtime errors into the branded
 * Maha Metro "Module Under Development" template.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final EmployeeRepository employees;

    public GlobalExceptionHandler(EmployeeRepository employees) {
        this.employees = employees;
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDenied(AccessDeniedException ex, HttpServletRequest request, Principal principal, Model model) {
        log.warn("Access denied on path '{}' for user '{}': {}", request.getRequestURI(), principal != null ? principal.getName() : "anonymous", ex.getMessage());
        populateModel(403, request.getRequestURI(), ex.getMessage(), principal, model);
        return "error";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGenericException(Exception ex, HttpServletRequest request, Principal principal, Model model) {
        log.error("Unhandled exception on path '{}': {}", request.getRequestURI(), ex.getMessage(), ex);
        populateModel(500, request.getRequestURI(), ex.getMessage(), principal, model);
        return "error";
    }

    private void populateModel(int status, String path, String message, Principal principal, Model model) {
        model.addAttribute("status", status);
        model.addAttribute("path", path);
        model.addAttribute("message", message);
        if (principal != null) {
            employees.findByEmpId(principal.getName()).ifPresent(emp -> model.addAttribute("employee", emp));
        }
    }
}
