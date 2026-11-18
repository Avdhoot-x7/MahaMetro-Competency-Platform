package in.mahametro.competency.auth;

import in.mahametro.competency.employees.EmployeeRepository;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Login page and the landing page after login. */
@Controller
public class AuthController {

    private final EmployeeRepository employees;

    public AuthController(EmployeeRepository employees) {
        this.employees = employees;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String home(Principal principal, Model model) {
        model.addAttribute("employee", employees.findByEmpId(principal.getName()).orElse(null));
        return "home";
    }
}
