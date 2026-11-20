package in.mahametro.competency.proctoring;

import in.mahametro.competency.delivery.*;
import in.mahametro.competency.framework.CompetencyRepository;
import in.mahametro.competency.questions.QuestionOptionRepository;
import in.mahametro.competency.questions.QuestionRepository;
import in.mahametro.competency.tests.AssessmentTest;
import in.mahametro.competency.tests.AssessmentTestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Unit tests verifying BehaviorAnalyticsService computes all 20 measures,
 * 6 headings, respects confidentiality rules (A5 & E1), and NEVER alters attempt scores.
 */
class BehaviorAnalyticsServiceTest {

    private AttemptRepository attemptRepository;
    private AttemptItemRepository attemptItemRepository;
    private AttemptEventRepository attemptEventRepository;
    private AssessmentTestRepository testRepository;
    private QuestionRepository questionRepository;
    private QuestionOptionRepository optionRepository;
    private CompetencyRepository competencyRepository;
    private BehaviorAnalyticsService service;

    @BeforeEach
    void setup() {
        attemptRepository = Mockito.mock(AttemptRepository.class);
        attemptItemRepository = Mockito.mock(AttemptItemRepository.class);
        attemptEventRepository = Mockito.mock(AttemptEventRepository.class);
        testRepository = Mockito.mock(AssessmentTestRepository.class);
        questionRepository = Mockito.mock(QuestionRepository.class);
        optionRepository = Mockito.mock(QuestionOptionRepository.class);
        competencyRepository = Mockito.mock(CompetencyRepository.class);

        service = new BehaviorAnalyticsService(
                attemptRepository,
                attemptItemRepository,
                attemptEventRepository,
                testRepository,
                questionRepository,
                optionRepository,
                competencyRepository
        );
    }

    private Attempt buildTestAttempt() {
        Attempt attempt = new Attempt(100L, 200L, "T0", 12345L, LocalDateTime.now().plusHours(1));
        attempt.setId(100L);
        attempt.setStartedAt(LocalDateTime.now().minusMinutes(45));
        attempt.setSubmittedAt(LocalDateTime.now().minusMinutes(5));
        attempt.setStatus(Attempt.STATUS_SCORED);
        attempt.setScore(BigDecimal.valueOf(18.0));
        attempt.setMaxScore(BigDecimal.valueOf(20.0));
        attempt.setPercent(BigDecimal.valueOf(90.0));
        attempt.setTabSwitches(1);
        return attempt;
    }

    private List<AttemptItem> buildItems() {
        List<AttemptItem> items = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            AttemptItem item = new AttemptItem(1L, (long) i, i, "CORE-01", "1", "single", BigDecimal.valueOf(2.0), "1,2,3,4");
            item.setTimeSpentSeconds(50);
            item.setSelectedOptions("1");
            item.setIsCorrect(i <= 8);
            items.add(item);
        }
        return items;
    }

    @Test
    void testAll20MeasuresAnd6HeadingsGenerated() {
        Attempt attempt = buildTestAttempt();
        AssessmentTest test = new AssessmentTest();
        test.setDurationMinutes(60);

        when(attemptItemRepository.findByAttemptIdOrderByPositionAsc(anyLong())).thenReturn(buildItems());
        when(attemptEventRepository.findByAttemptIdOrderByAtAsc(anyLong())).thenReturn(List.of());
        when(testRepository.findById(anyLong())).thenReturn(Optional.of(test));
        when(attemptRepository.findByEmployeeIdOrderByStartedAtDesc(anyLong())).thenReturn(List.of(attempt));

        BehaviorAnalyticsService.BehaviorReport report = service.generateReport(attempt, true);

        assertNotNull(report);
        assertEquals(6, report.getHeadings().size(), "Must generate exactly 6 reporting headings");

        // Verify the 6 headings
        List<String> expectedHeadings = List.of(
                "Test-taking behaviour",
                "Answering behaviour",
                "Time management",
                "Solution engagement",
                "Learning behaviour",
                "Consistency"
        );
        for (int i = 0; i < 6; i++) {
            assertEquals(expectedHeadings.get(i), report.getHeadings().get(i).getHeading());
        }

        // Verify all 20 measures are present
        String[] codes = {"A1", "A2", "A3", "A4", "A5", "B1", "B2", "B3", "C1", "C2", "C3", "C4", "D1", "D2", "D3", "D4", "E1", "E2", "E3", "E4"};
        for (String c : codes) {
            assertTrue(report.getMeasuresByCode().containsKey(c), "Missing measure: " + c);
            assertNotNull(report.getMeasuresByCode().get(c).getValue(), "Measure " + c + " must have a value");
        }
    }

    @Test
    void testConfidentialMeasuresMaskedForRegularUsers() {
        Attempt attempt = buildTestAttempt();
        attempt.setTabSwitches(6); // Over limit -> triggers E1 flag

        AssessmentTest test = new AssessmentTest();
        test.setDurationMinutes(60);
        test.setMaxTabSwitches(3);

        when(attemptItemRepository.findByAttemptIdOrderByPositionAsc(anyLong())).thenReturn(buildItems());
        when(attemptEventRepository.findByAttemptIdOrderByAtAsc(anyLong())).thenReturn(List.of());
        when(testRepository.findById(anyLong())).thenReturn(Optional.of(test));
        when(attemptRepository.findByEmployeeIdOrderByStartedAtDesc(anyLong())).thenReturn(List.of(attempt));

        // 1. Privileged viewer (Proctor / Cell): sees unmasked E1 flag
        BehaviorAnalyticsService.BehaviorReport proctorReport = service.generateReport(attempt, true);
        BehaviorAnalyticsService.BehaviorMeasure proctorE1 = proctorReport.getMeasuresByCode().get("E1");
        assertTrue(proctorE1.isFlagged(), "Proctor must see flagged rule compliance E1");
        assertTrue(proctorE1.getValue().contains("6 tab switch(es)"));

        // 2. Regular candidate viewer: E1 is masked
        BehaviorAnalyticsService.BehaviorReport candidateReport = service.generateReport(attempt, false);
        BehaviorAnalyticsService.BehaviorMeasure candidateE1 = candidateReport.getMeasuresByCode().get("E1");
        assertFalse(candidateE1.isFlagged(), "Candidate report must not show active confidential flag");
        assertTrue(candidateE1.getValue().contains("Confidential Proctor Metric"), "Candidate must see confidential mask");
    }

    @Test
    void testBehaviorAnalyticsNeverAltersAttemptScore() {
        Attempt attempt = buildTestAttempt();
        BigDecimal originalScore = attempt.getScore();
        BigDecimal originalPercent = attempt.getPercent();
        String originalStatus = attempt.getStatus();

        AssessmentTest test = new AssessmentTest();
        test.setDurationMinutes(60);

        when(attemptItemRepository.findByAttemptIdOrderByPositionAsc(anyLong())).thenReturn(buildItems());
        when(attemptEventRepository.findByAttemptIdOrderByAtAsc(anyLong())).thenReturn(List.of());
        when(testRepository.findById(anyLong())).thenReturn(Optional.of(test));
        when(attemptRepository.findByEmployeeIdOrderByStartedAtDesc(anyLong())).thenReturn(List.of(attempt));

        // Execute report multiple times
        service.generateReport(attempt, false);
        service.generateReport(attempt, true);

        // Verify score and status are 100% immutable
        assertEquals(originalScore, attempt.getScore(), "Attempt score must never be altered by behavior analytics");
        assertEquals(originalPercent, attempt.getPercent(), "Attempt percent must never be altered by behavior analytics");
        assertEquals(originalStatus, attempt.getStatus(), "Attempt status must never be altered by behavior analytics");
    }
}
