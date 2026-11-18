package in.mahametro.competency.core;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ForwardedHeaderFilter;

/** Shared web settings. */
@Configuration
public class WebConfig {

    /** Makes the app see the real scheme, host and client IP when it runs behind Nginx or Apache in front of Tomcat. */
    @Bean
    public ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }
}
