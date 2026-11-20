package in.mahametro.competency.questions;

import in.mahametro.competency.delivery.Attempt;
import in.mahametro.competency.delivery.AttemptItem;
import in.mahametro.competency.delivery.AttemptItemRepository;
import in.mahametro.competency.delivery.AttemptRepository;
import in.mahametro.competency.tests.AssessmentTestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Unit tests verifying ItemAnalysisService computes facility p,
 * point-biserial discrimination r_pb, and flags according to Section 9.
 */
class ItemAnalysisServiceTest {

    private QuestionRepository questionRepository;
    private QuestionOptionRepository optionRepository;
    private AttemptItemRepository attemptItemRepository;
    private AttemptRepository attemptRepository;
    private AssessmentTestRepository testRepository;
    private ItemAnalysisService service;

    @BeforeEach
    void setup() {
        questionRepository = Mockito.mock(QuestionRepository.class);
        optionRepository = Mockito.mock(QuestionOptionRepository.class);
        attemptItemRepository = Mockito.mock(AttemptItemRepository.class);
        attemptRepository = Mockito.mock(AttemptRepository.class);
        testRepository = Mockito.mock(AssessmentTestRepository.class);

        service = new ItemAnalysisService(
                questionRepository,
                optionRepository,
                attemptItemRepository,
                attemptRepository,
                testRepository
        );
    }

    private Question createQuestion(Long id, String code) {
        Question q = new Question(code, "CORE-01", "Sample Question Stem", "EMP01");
        q.setId(id);
        q.setStatus(Question.STATUS_APPROVED);
        return q;
    }

    @Test
    void testHealthyItemPsychometrics() {
        Question q = createQuestion(1L, "Q-HEALTHY");

        // Simulate 20 attempts: 14 correct, 6 wrong.
        // Correct responders have rest score 80.0, wrong responders have rest score 40.0
        List<AttemptItem> items = new ArrayList<>();
        for (long i = 1; i <= 20; i++) {
            boolean isCorrect = i <= 14;
            AttemptItem item = new AttemptItem(i, q.getId(), 1, "CORE-01", "1", "single", BigDecimal.ONE, "1,2,3,4");
            item.setIsCorrect(isCorrect);
            item.setAwardedScore(isCorrect ? BigDecimal.ONE : BigDecimal.ZERO);
            item.setSelectedOptions(isCorrect ? "1" : "2");
            items.add(item);

            Attempt att = new Attempt(100L, i, "T0", 123L, null);
            att.setStatus(Attempt.STATUS_SCORED);
            att.setScore(BigDecimal.valueOf(isCorrect ? 81.0 : 40.0));
            when(attemptRepository.findById(i)).thenReturn(Optional.of(att));
        }

        when(attemptItemRepository.findByQuestionId(q.getId())).thenReturn(items);
        when(optionRepository.findByQuestionIdOrderByOrdAsc(q.getId())).thenReturn(List.of());

        ItemAnalysisService.ItemAnalysisResult result = service.analyzeQuestion(q, false);

        assertEquals(20, result.getN());
        assertEquals(0.70, result.getPValue(), 0.01, "Facility p should be 14/20 = 0.70");
        assertNotNull(result.getPointBiserial());
        assertTrue(result.getPointBiserial() > 0.30, "Discrimination should be strong and positive");
        assertFalse(result.isNegativeDiscrimination());
        assertTrue(result.getAction().contains("Retain"));
    }

    @Test
    void testTooHardItemFlagged() {
        Question q = createQuestion(2L, "Q-HARD");

        // 20 attempts: only 3 correct (p = 0.15 < 0.30)
        List<AttemptItem> items = new ArrayList<>();
        for (long i = 1; i <= 20; i++) {
            boolean isCorrect = i <= 3;
            AttemptItem item = new AttemptItem(i, q.getId(), 1, "CORE-01", "1", "single", BigDecimal.ONE, "1,2,3,4");
            item.setIsCorrect(isCorrect);
            item.setAwardedScore(isCorrect ? BigDecimal.ONE : BigDecimal.ZERO);
            items.add(item);

            Attempt att = new Attempt(100L, i, "T0", 123L, null);
            att.setStatus(Attempt.STATUS_SCORED);
            att.setScore(BigDecimal.valueOf(isCorrect ? 60.0 : 40.0));
            when(attemptRepository.findById(i)).thenReturn(Optional.of(att));
        }

        when(attemptItemRepository.findByQuestionId(q.getId())).thenReturn(items);
        when(optionRepository.findByQuestionIdOrderByOrdAsc(q.getId())).thenReturn(List.of());

        ItemAnalysisService.ItemAnalysisResult result = service.analyzeQuestion(q, false);

        assertEquals(0.15, result.getPValue(), 0.01);
        assertTrue(result.getFlags().stream().anyMatch(f -> f.contains("Too hard")));
        assertTrue(result.getAction().contains("Back to panel"));
    }

    @Test
    void testNegativeDiscriminationTriggersImmediateRemoval() {
        Question q = createQuestion(3L, "Q-WRONG-KEY");

        // 20 attempts: strong candidates (high rest score 80) got it wrong;
        // weak candidates (low rest score 30) got it "right" (indicates wrong answer key)
        List<AttemptItem> items = new ArrayList<>();
        for (long i = 1; i <= 20; i++) {
            boolean isCorrect = i <= 10;
            AttemptItem item = new AttemptItem(i, q.getId(), 1, "CORE-01", "1", "single", BigDecimal.ONE, "1,2,3,4");
            item.setIsCorrect(isCorrect);
            item.setAwardedScore(isCorrect ? BigDecimal.ONE : BigDecimal.ZERO);
            items.add(item);

            Attempt att = new Attempt(100L, i, "T0", 123L, null);
            att.setStatus(Attempt.STATUS_SCORED);
            // Incorrect group has high score (80), correct group has low score (30) -> r < 0
            att.setScore(BigDecimal.valueOf(isCorrect ? 31.0 : 80.0));
            when(attemptRepository.findById(i)).thenReturn(Optional.of(att));
        }

        when(attemptItemRepository.findByQuestionId(q.getId())).thenReturn(items);
        when(optionRepository.findByQuestionIdOrderByOrdAsc(q.getId())).thenReturn(List.of());

        ItemAnalysisService.ItemAnalysisResult result = service.analyzeQuestion(q, true);

        assertTrue(result.isNegativeDiscrimination(), "r < 0 must be flagged as negative discrimination");
        assertTrue(result.getFlags().stream().anyMatch(f -> f.contains("Negative discrimination")));
        assertTrue(result.getAction().contains("Remove from exams at once"));
        // Check that question was automatically returned to review pool
        assertEquals(Question.STATUS_REVIEW, q.getStatus(), "Question with r < 0 must be reverted to review status");
    }
}
