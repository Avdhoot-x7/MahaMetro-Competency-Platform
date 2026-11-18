package in.mahametro.competency.reporting;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Ensures stored profile summaries are pre-populated on startup when migrating to V14.
 */
@Component
public class SummaryInitializer implements ApplicationRunner {

    private final ProfileSummaryRepository summaries;
    private final ReportingService reportingService;

    public SummaryInitializer(ProfileSummaryRepository summaries, ReportingService reportingService) {
        this.summaries = summaries;
        this.reportingService = reportingService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (summaries.count() == 0) {
            reportingService.refreshAllSummaries();
        }
    }
}
