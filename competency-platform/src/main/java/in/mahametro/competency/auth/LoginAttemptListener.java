package in.mahametro.competency.auth;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/** Passes login results from Spring Security to LoginAttemptService. */
@Component
public class LoginAttemptListener {

    private final LoginAttemptService attempts;

    public LoginAttemptListener(LoginAttemptService attempts) {
        this.attempts = attempts;
    }

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        attempts.failed(event.getAuthentication().getName());
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        attempts.succeeded(event.getAuthentication().getName());
    }
}
