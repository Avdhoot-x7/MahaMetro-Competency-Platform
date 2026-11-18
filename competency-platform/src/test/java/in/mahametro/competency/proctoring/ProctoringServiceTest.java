package in.mahametro.competency.proctoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import in.mahametro.competency.delivery.Attempt;
import in.mahametro.competency.delivery.AttemptItem;
import in.mahametro.competency.delivery.AttemptItemRepository;
import in.mahametro.competency.delivery.AttemptRepository;
import in.mahametro.competency.delivery.DeliveryService;
import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.employees.EmployeeRepository;
import in.mahametro.competency.questions.Question;
import in.mahametro.competency.questions.QuestionOption;
import in.mahametro.competency.questions.QuestionOptionRepository;
import in.mahametro.competency.questions.QuestionRepository;
import in.mahametro.competency.scoring.CompetencyScore;
import in.mahametro.competency.scoring.CompetencyScoreRepository;
import in.mahametro.competency.tests.AssessmentTest;
import in.mahametro.competency.tests.AssessmentTestRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ProctoringServiceTest {

    @Autowired
    ProctoringService proctoringService;

    @Autowired
    TimingAnalysisService timingService;

    @Autowired
    DeliveryService deliveryService;

    @Autowired
    AttemptRepository attemptRepository;

    @Autowired
    AttemptItemRepository attemptItemRepository;

    @Autowired
    AssessmentTestRepository testRepository;

    @Autowired
    EmployeeRepository employeeRepository;

    @Autowired
    QuestionRepository questionRepository;

    @Autowired
    QuestionOptionRepository optionRepository;

    @Autowired
    CompetencyScoreRepository scoreRepository;

    private Employee candidate;
    private Employee proctor;
    private AssessmentTest assessmentTest;
    private Question question;

    @BeforeEach
    void setUp() {
        candidate = new Employee("CAND-PROC-" + System.nanoTime(), "Test Candidate");
        candidate.setDepartment("Operations");
        candidate.setRoleFamilyCode("SC");
        candidate = employeeRepository.save(candidate);

        proctor = new Employee("PROC-OFF-" + System.nanoTime(), "Proctor Officer");
        proctor = employeeRepository.save(proctor);

        assessmentTest = new AssessmentTest("T-PROC-" + System.nanoTime(), "Proctoring Test", "T0", 60);
        assessmentTest.setStatus(AssessmentTest.STATUS_PUBLISHED);
        assessmentTest = testRepository.save(assessmentTest);

        question = new Question("Q-PROC-" + System.nanoTime(), "C01",
                "What is the standard procedure for emergency braking?", candidate.getEmpId());
        question.setStatus(Question.APPROVED);
        question = questionRepository.save(question);

        QuestionOption opt1 = new QuestionOption(question.getId(), 1, "Apply emergency brake handle", true);
        QuestionOption opt2 = new QuestionOption(question.getId(), 2, "Do nothing", false);
        optionRepository.save(opt1);
        optionRepository.save(opt2);
    }

    @Test
    void testRecordEventAndTabSwitches() {
        Attempt attempt = new Attempt(assessmentTest.getId(), candidate.getId(), "T0", 12345L, LocalDateTime.now().plusHours(1));
        attempt = attemptRepository.save(attempt);

        int switches = proctoringService.recordEvent(attempt.getId(), candidate, ProctorEvent.KIND_TAB_HIDDEN, "User switched tab", null);
        assertEquals(1, switches);

        proctoringService.recordEvent(attempt.getId(), candidate, ProctorEvent.KIND_BLUR, "Lost window focus", null);
        Attempt reloaded = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertEquals(2, reloaded.getTabSwitches());

        // Copy event should not increment tab switches
        proctoringService.recordEvent(attempt.getId(), candidate, ProctorEvent.KIND_COPY, "Copy prevented", null);
        reloaded = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertEquals(2, reloaded.getTabSwitches());

        List<ProctorEvent> events = proctoringService.getEventsForAttempt(attempt.getId());
        assertEquals(3, events.size());
    }

    @Test
    void testTimingThresholdAndRapidGuessing() {
        double threshold = timingService.calculateThreshold(question);
        assertTrue(threshold >= 3.0, "Threshold should be at least 3 seconds");

        Attempt attempt = new Attempt(assessmentTest.getId(), candidate.getId(), "T0", 12345L, LocalDateTime.now().plusHours(1));
        attempt = attemptRepository.save(attempt);

        AttemptItem item = new AttemptItem(attempt.getId(), question.getId(), 1, "C01", "101", "single",
                BigDecimal.ONE, "1,2");
        item.setSelectedOptions("1");
        item.setTimeSpentSeconds(1); // 1s is below reading threshold -> rapid answer
        item = attemptItemRepository.save(item);

        TimingAnalysisService.TimingSummary summary = timingService.analyse(attempt, List.of(item));
        assertEquals(1, summary.getAnsweredCount());
        assertEquals(1, summary.getRapidCount());
        assertEquals(0.0, summary.getRte());
        assertTrue(summary.isLowEffort(), "Should be flagged as low effort");
    }

    @Test
    void testCheckIntegrityFlagsViolations() {
        Attempt attempt = new Attempt(assessmentTest.getId(), candidate.getId(), "T0", 12345L, LocalDateTime.now().plusHours(1));
        attempt.setTabSwitches(5); // Exceeds limit of 3
        attempt = attemptRepository.save(attempt);

        AttemptItem item = new AttemptItem(attempt.getId(), question.getId(), 1, "C01", "101", "single",
                BigDecimal.ONE, "1,2");
        item.setSelectedOptions("1");
        item.setTimeSpentSeconds(10); // Normal time

        boolean clean = proctoringService.checkIntegrity(attempt, List.of(item), assessmentTest);
        assertFalse(clean, "Integrity check should fail for tab switches > 3");
        assertEquals(Attempt.STATUS_FLAGGED, attempt.getStatus());
        assertTrue(attempt.getFlagReason().contains("Exceeded tab switch limit"));
    }

    @Test
    void testProctorAcceptAndVoidWorkflow() {
        Attempt attempt = new Attempt(assessmentTest.getId(), candidate.getId(), "T0", 12345L, LocalDateTime.now().plusHours(1));
        attempt.setStatus(Attempt.STATUS_FLAGGED);
        attempt.setFlagReason("Exceeded tab switch limit (4 switches)");
        attempt = attemptRepository.save(attempt);

        AttemptItem item = new AttemptItem(attempt.getId(), question.getId(), 1, "C01", "101", "single",
                BigDecimal.ONE, "1,2");
        item.setSelectedOptions("1");
        item.setAwardedScore(BigDecimal.ONE);
        attemptItemRepository.save(item);

        // Accept
        proctoringService.acceptAttempt(attempt.getId(), proctor, "Candidate explained accident with touchpad");
        Attempt accepted = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertEquals(Attempt.STATUS_SCORED, accepted.getStatus());
        assertEquals(proctor.getEmpId(), accepted.getReviewedBy());
        assertNotNull(accepted.getReviewedAt());
        assertTrue(accepted.getFlagReason().contains("ACCEPTED"));

        // Verify competency scores published upon acceptance
        List<CompetencyScore> scores = scoreRepository.findByEmployeeId(candidate.getId());
        assertFalse(scores.isEmpty(), "Scores should be published on acceptance");

        // Void
        proctoringService.voidAttempt(attempt.getId(), proctor, "Candidate cheated");
        Attempt voided = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertEquals(Attempt.STATUS_VOID, voided.getStatus());
        assertTrue(voided.getFlagReason().contains("VOIDED"));

        // Verify competency scores deleted on void
        scores = scoreRepository.findByEmployeeId(candidate.getId());
        assertTrue(scores.isEmpty(), "Scores should be revoked on void");
    }
}
