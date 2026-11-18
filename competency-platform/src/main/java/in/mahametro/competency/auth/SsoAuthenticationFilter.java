package in.mahametro.competency.auth;

import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.employees.EmployeeRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Single Sign-On (SSO) filter for enterprise reverse-proxy and intranet portal environments.
 * When enabled, extracts the authenticated employee ID from a trusted gateway header
 * (e.g. X-Remote-User, X-Forwarded-User) after verifying the request originates from
 * an authorized proxy IP, bypassing manual password prompts.
 */
@Component
public class SsoAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SsoAuthenticationFilter.class);

    @Value("${app.sso.header-enabled:false}")
    private boolean headerSsoEnabled;

    @Value("${app.sso.header-name:X-Remote-User}")
    private String ssoHeaderName;

    @Value("${app.sso.trusted-proxies:127.0.0.1,::1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16}")
    private String trustedProxiesConfig;

    private final EmployeeRepository employeeRepository;
    private final EmployeeUserDetailsService userDetailsService;

    public SsoAuthenticationFilter(EmployeeRepository employeeRepository,
            EmployeeUserDetailsService userDetailsService) {
        this.employeeRepository = employeeRepository;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!headerSsoEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        // If already authenticated, proceed
        if (SecurityContextHolder.getContext().getAuthentication() != null &&
                SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String remoteUser = request.getHeader(ssoHeaderName);
        if (remoteUser == null || remoteUser.trim().isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        remoteUser = remoteUser.trim();
        String clientIp = getClientIp(request);

        if (!isTrustedProxy(clientIp)) {
            log.warn("Rejected SSO header '{}' from untrusted client IP: {}", ssoHeaderName, clientIp);
            filterChain.doFilter(request, response);
            return;
        }

        // Lookup employee by emp_id
        Employee emp = employeeRepository.findByEmpId(remoteUser).orElse(null);
        if (emp != null && emp.isActive()) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(emp.getEmpId());
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
            log.info("SSO authenticated user '{}' ({}) via header {} from proxy {}",
                    emp.getEmpId(), emp.getNameEn(), ssoHeaderName, clientIp);
        } else {
            log.warn("SSO header provided unknown or inactive employee ID: {}", remoteUser);
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isTrustedProxy(String ip) {
        if (ip == null) {
            return false;
        }
        List<String> trusted = Arrays.asList(trustedProxiesConfig.split(","));
        for (String t : trusted) {
            String rule = t.trim();
            if (rule.equals(ip) || rule.equals("0.0.0.0/0") || ip.startsWith(rule)) {
                return true;
            }
            // Simple loopback and local prefix checks
            if (("127.0.0.1".equals(ip) || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) &&
                    (rule.contains("127.0.0.1") || rule.contains("::1"))) {
                return true;
            }
        }
        return false;
    }

    public void setHeaderSsoEnabled(boolean headerSsoEnabled) {
        this.headerSsoEnabled = headerSsoEnabled;
    }

    public void setSsoHeaderName(String ssoHeaderName) {
        this.ssoHeaderName = ssoHeaderName;
    }

    public void setTrustedProxiesConfig(String trustedProxiesConfig) {
        this.trustedProxiesConfig = trustedProxiesConfig;
    }
}
