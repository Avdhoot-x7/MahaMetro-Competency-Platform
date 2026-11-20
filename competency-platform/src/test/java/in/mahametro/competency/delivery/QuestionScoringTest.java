package in.mahametro.competency.delivery;

import in.mahametro.competency.questions.Question;
import in.mahametro.competency.questions.QuestionOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests verifying scoring algorithms across all question types
 * according to Section 6 of the Spec.
 */
class QuestionScoringTest {

    private DeliveryService deliveryService;

    @BeforeEach
    void setup() {
        // DeliveryService with 14 null dependencies for pure calculation unit testing
        deliveryService = new DeliveryService(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void testSingleChoiceScoring() {
        Question q = new Question("Q1", "CORE-01", "Single choice question", "EMP1");
        q.setQtype("single");
        List<QuestionOption> options = List.of(
                new QuestionOption(1L, 100L, 1, "Correct opt", "सत्य", true, ""),
                new QuestionOption(2L, 100L, 2, "Wrong opt", "असत्य", false, "Misconception")
        );

        AttemptItem itemCorrect = new AttemptItem(1L, 1L, 1, "CORE-01", "1", "single", BigDecimal.valueOf(2.0), "1,2");
        itemCorrect.setSelectedOptions("1");
        BigDecimal scoreCorrect = deliveryService.calculateItemScore(itemCorrect, q, options);
        assertEquals(0, scoreCorrect.compareTo(BigDecimal.valueOf(2.0)));

        AttemptItem itemWrong = new AttemptItem(1L, 1L, 1, "CORE-01", "1", "single", BigDecimal.valueOf(2.0), "1,2");
        itemWrong.setSelectedOptions("2");
        BigDecimal scoreWrong = deliveryService.calculateItemScore(itemWrong, q, options);
        assertEquals(0, scoreWrong.compareTo(BigDecimal.ZERO));
    }

    @Test
    void testMultiSelectPartialCredit() {
        Question q = new Question("Q2", "CORE-01", "Multi select question", "EMP1");
        q.setQtype("multi");
        List<QuestionOption> options = List.of(
                new QuestionOption(10L, 200L, 1, "Correct 1", "सत्य १", true, ""),
                new QuestionOption(20L, 200L, 2, "Correct 2", "सत्य २", true, ""),
                new QuestionOption(30L, 200L, 3, "Wrong 1", "असत्य १", false, "Mistake 1"),
                new QuestionOption(40L, 200L, 4, "Wrong 2", "असत्य २", false, "Mistake 2")
        );

        AttemptItem item = new AttemptItem(1L, 2L, 1, "CORE-01", "1", "multi", BigDecimal.valueOf(4.0), "10,20,30,40");

        // 1. Both correct chosen -> full marks 4.0
        item.setSelectedOptions("10,20");
        BigDecimal fullScore = deliveryService.calculateItemScore(item, q, options);
        assertEquals(0, fullScore.compareTo(BigDecimal.valueOf(4.0)));

        // 2. 1 correct out of 2 chosen -> partial 2.0
        item.setSelectedOptions("10");
        BigDecimal halfScore = deliveryService.calculateItemScore(item, q, options);
        assertEquals(0, halfScore.compareTo(BigDecimal.valueOf(2.0)));

        // 3. 1 correct and 1 wrong -> (1/2 - 1/2) = 0.0
        item.setSelectedOptions("10,30");
        BigDecimal zeroScore = deliveryService.calculateItemScore(item, q, options);
        assertEquals(0, zeroScore.compareTo(BigDecimal.ZERO));

        // 4. Only wrong options -> floored at 0
        item.setSelectedOptions("30,40");
        BigDecimal negFloorScore = deliveryService.calculateItemScore(item, q, options);
        assertEquals(0, negFloorScore.compareTo(BigDecimal.ZERO));
    }

    @Test
    void testSjtWeightedScoring() {
        Question q = new Question("Q3", "BEH-01", "SJT scenario", "EMP1");
        q.setQtype("sjt");
        QuestionOption optBest = new QuestionOption(101L, 300L, 1, "Best response", "उत्कृष्ट", true, "");
        optBest.setWeight(1.0);

        QuestionOption optGood = new QuestionOption(102L, 300L, 2, "Acceptable response", "चांगले", false, "");
        optGood.setWeight(0.5);

        QuestionOption optPoor = new QuestionOption(103L, 300L, 3, "Ineffective response", "अयोग्य", false, "Inaction");
        optPoor.setWeight(0.0);

        List<QuestionOption> options = List.of(optBest, optGood, optPoor);
        AttemptItem item = new AttemptItem(1L, 3L, 1, "BEH-01", "1", "sjt", BigDecimal.valueOf(3.0), "101,102,103");

        // Best chosen -> 3.0 * 1.0 = 3.0
        item.setSelectedOptions("101");
        assertEquals(0, deliveryService.calculateItemScore(item, q, options).compareTo(BigDecimal.valueOf(3.0)));

        // Acceptable chosen -> 3.0 * 0.5 = 1.5
        item.setSelectedOptions("102");
        assertEquals(0, deliveryService.calculateItemScore(item, q, options).compareTo(BigDecimal.valueOf(1.5)));

        // Ineffective chosen -> 0.0
        item.setSelectedOptions("103");
        assertEquals(0, deliveryService.calculateItemScore(item, q, options).compareTo(BigDecimal.ZERO));
    }

    @Test
    void testNumericToleranceScoring() {
        Question q = new Question("Q4", "DOM-01", "Enter pressure in bar", "EMP1");
        q.setQtype("numeric");
        q.setAnswerNumeric(25.0);
        q.setNumericTolerance(0.5); // Valid: 24.5 to 25.5

        AttemptItem item = new AttemptItem(1L, 4L, 1, "DOM-01", "1", "numeric", BigDecimal.valueOf(2.0), "");

        // Exactly correct
        item.setAnsweredText("25.0");
        assertEquals(0, deliveryService.calculateItemScore(item, q, List.of()).compareTo(BigDecimal.valueOf(2.0)));

        // Within tolerance (24.8)
        item.setAnsweredText("24.8");
        assertEquals(0, deliveryService.calculateItemScore(item, q, List.of()).compareTo(BigDecimal.valueOf(2.0)));

        // Outside tolerance (23.9)
        item.setAnsweredText("23.9");
        assertEquals(0, deliveryService.calculateItemScore(item, q, List.of()).compareTo(BigDecimal.ZERO));
    }

    @Test
    void testFillInTheBlankAcceptedAnswers() {
        Question q = new Question("Q5", "CORE-02", "What is OCC?", "EMP1");
        q.setQtype("fill_blank");
        q.setAcceptedAnswers("Operations Control Centre,Operation Control Center, OCC");

        AttemptItem item = new AttemptItem(1L, 5L, 1, "CORE-02", "1", "fill_blank", BigDecimal.valueOf(1.0), "");

        // Matches case-insensitively with whitespace trimmed
        item.setAnsweredText("  operations control centre ");
        assertEquals(0, deliveryService.calculateItemScore(item, q, List.of()).compareTo(BigDecimal.valueOf(1.0)));

        item.setAnsweredText("OCC");
        assertEquals(0, deliveryService.calculateItemScore(item, q, List.of()).compareTo(BigDecimal.valueOf(1.0)));

        // Wrong input
        item.setAnsweredText("Onboard Computer Controller");
        assertEquals(0, deliveryService.calculateItemScore(item, q, List.of()).compareTo(BigDecimal.ZERO));
    }
}
