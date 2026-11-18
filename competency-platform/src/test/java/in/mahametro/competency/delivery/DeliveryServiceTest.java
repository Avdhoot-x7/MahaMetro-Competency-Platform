package in.mahametro.competency.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
class DeliveryServiceTest {

    @Autowired
    DeliveryService deliveryService;

    @Autowired
    AssessmentTestRepository testRepository;

    @Autowired
    QuestionRepository questionRepository;

    @Autowired
    QuestionOptionRepository optionRepository;

    @Autowired
    EmployeeRepository employeeRepository;

    @Autowired
    CompetencyScoreRepository scoreRepository;

    private Employee candidate;
    private AssessmentTest assessmentTest;
    private Question q1;
    private QuestionOption q1Correct;
    private QuestionOption q1Wrong;

    @BeforeEach
    void setUp() {
        candidate = new Employee("CAND-DEL-" + System.nanoTime(), "Delivery Candidate");
        candidate.setDepartment("Operations");
        candidate.setRoleFamilyCode("SC");
        candidate = employeeRepository.save(candidate);

        assessmentTest = new AssessmentTest("TEST-DEL-" + System.nanoTime(), "Delivery Test", AssessmentTest.KIND_T0, 60);
        assessmentTest.setStatus(AssessmentTest.STATUS_PUBLISHED);
        assessmentTest.setTargetType(AssessmentTest.TARGET_ALL);
        assessmentTest.setItemsPerCompetency(5);
        assessmentTest = testRepository.save(assessmentTest);

        q1 = new Question("Q-DEL-" + System.nanoTime(), "C01", "What is the speed limit?", "AUTHOR");
        q1.setTopicNo("1");
        q1.setStatus(Question.APPROVED);
        q1 = questionRepository.save(q1);

        q1Correct = optionRepository.save(new QuestionOption(q1.getId(), 0, "25 km/h", true));
        q1Wrong = optionRepository.save(new QuestionOption(q1.getId(), 1, "90 km/h", false));
    }

    @Test
    void testStartResumeAutosaveAndSubmit() {
        // 1. Start attempt
        Attempt attempt = deliveryService.startOrResumeAttempt(assessmentTest.getId(), candidate);
        assertNotNull(attempt.getId());
        assertEquals(Attempt.STATUS_IN_PROGRESS, attempt.getStatus());
        assertTrue(attempt.getDeadlineAt().isAfter(LocalDateTime.now().plusMinutes(55)));

        // 2. Resume attempt returns same attempt
        Attempt resumed = deliveryService.startOrResumeAttempt(assessmentTest.getId(), candidate);
        assertEquals(attempt.getId(), resumed.getId());

        // 3. Record answer (autosave)
        List<AttemptItem> items = deliveryService.getItems(attempt.getId());
        assertFalse(items.isEmpty());
        AttemptItem firstItem = items.get(0);

        deliveryService.recordAnswer(attempt.getId(), candidate, firstItem.getPosition(),
                String.valueOf(q1Correct.getId()), "", 15);

        List<AttemptItem> updatedItems = deliveryService.getItems(attempt.getId());
        assertEquals(String.valueOf(q1Correct.getId()), updatedItems.get(0).getSelectedOptions());
        assertEquals(15, updatedItems.get(0).getTimeSpentSeconds());

        // 4. Submit attempt and auto-mark
        Attempt submitted = deliveryService.submitAttempt(attempt.getId(), candidate);
        assertEquals(Attempt.STATUS_SCORED, submitted.getStatus());
        assertNotNull(submitted.getSubmittedAt());
        assertTrue(submitted.getScore().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(new BigDecimal("100.00"), submitted.getPercent());

        // 5. Verify measured competency score was generated (Goal 5)
        List<CompetencyScore> scores = scoreRepository.findByEmployeeId(candidate.getId());
        assertFalse(scores.isEmpty());
        assertTrue(scores.stream().anyMatch(s -> "C01".equals(s.getCompetencyCode()) && s.getScore().doubleValue() == 100.0));
    }
}
