package in.mahametro.competency.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.employees.EmployeeRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

@ExtendWith(MockitoExtension.class)
class SsoAuthenticationFilterTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private EmployeeUserDetailsService userDetailsService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private SsoAuthenticationFilter filter;
    private Employee testEmployee;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new SsoAuthenticationFilter(employeeRepository, userDetailsService);
        testEmployee = new Employee("EMP001", "Asha Kulkarni");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testSsoDisabledDoesNotAuthenticate() throws Exception {
        filter.setHeaderSsoEnabled(false);
        filter.setSsoHeaderName("X-Remote-User");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(employeeRepository);
    }

    @Test
    void testSsoEnabledWithValidHeaderAndTrustedProxyAuthenticates() throws Exception {
        filter.setHeaderSsoEnabled(true);
        filter.setSsoHeaderName("X-Remote-User");
        filter.setTrustedProxiesConfig("127.0.0.1,::1");

        when(request.getHeader("X-Remote-User")).thenReturn("EMP001");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        when(employeeRepository.findByEmpId("EMP001")).thenReturn(Optional.of(testEmployee));
        User userDetails = new User("EMP001", "secret",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));
        when(userDetailsService.loadUserByUsername("EMP001")).thenReturn(userDetails);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("EMP001");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority").contains("ROLE_EMPLOYEE");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testSsoRejectsUntrustedProxyIp() throws Exception {
        filter.setHeaderSsoEnabled(true);
        filter.setSsoHeaderName("X-Remote-User");
        filter.setTrustedProxiesConfig("127.0.0.1,10.0.0.0");

        when(request.getHeader("X-Remote-User")).thenReturn("EMP001");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.195"); // external untrusted public IP

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(employeeRepository);
    }

    @Test
    void testSsoUnknownEmployeePassesThrough() throws Exception {
        filter.setHeaderSsoEnabled(true);
        filter.setSsoHeaderName("X-Remote-User");
        filter.setTrustedProxiesConfig("127.0.0.1");

        when(request.getHeader("X-Remote-User")).thenReturn("UNKNOWN-USER");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        when(employeeRepository.findByEmpId("UNKNOWN-USER")).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
