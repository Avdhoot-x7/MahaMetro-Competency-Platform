package in.mahametro.competency;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Entry point. Runs standalone (mvn spring-boot:run) for development and as a WAR on Apache Tomcat in production.
 */
@SpringBootApplication
public class CompetencyApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(CompetencyApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(CompetencyApplication.class, args);
    }
}
