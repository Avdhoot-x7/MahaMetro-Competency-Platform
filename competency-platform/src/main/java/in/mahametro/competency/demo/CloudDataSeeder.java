package in.mahametro.competency.demo;

import in.mahametro.competency.employees.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Automatically seeds the full 100-employee enterprise demonstration dataset
 * (employees, single roles, competency scores, assessment attempts, development plans,
 * and profile summaries) if the target database has fewer than 20 employees.
 * This guarantees that new cloud deployments (Railway, Render, AWS, etc.) instantly
 * have all dashboards, scorecards, and demo accounts available for review.
 */
@Component
@Profile("!test")
@Order(100)
public class CloudDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CloudDataSeeder.class);

    private final EmployeeRepository employeeRepository;
    private final DataSource dataSource;

    public CloudDataSeeder(EmployeeRepository employeeRepository, DataSource dataSource) {
        this.employeeRepository = employeeRepository;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            long count = employeeRepository.count();
            if (count < 20) {
                log.info("Database has {} employees (< 20). Seeding full enterprise dataset (100 employees, test results, scorecards)...", count);
                ClassPathResource resource = new ClassPathResource("db/seed_100_employees.sql");
                if (resource.exists()) {
                    ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
                    populator.addScript(resource);
                    populator.setContinueOnError(true);
                    populator.setIgnoreFailedDrops(true);
                    populator.execute(dataSource);
                    log.info("Enterprise dataset seeded successfully! Current employee count: {}", employeeRepository.count());
                } else {
                    log.warn("Seed script db/seed_100_employees.sql not found on classpath.");
                }
            } else {
                log.info("Database already contains {} employees. Skipping cloud dataset seeding.", count);
            }
        } catch (Exception e) {
            log.error("Notice during enterprise dataset check/seeding: {}", e.getMessage());
        }
    }
}
