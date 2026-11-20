package in.mahametro.competency.questions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuestionQualityValidatorTest {

    private QuestionQualityValidator validator;

    @BeforeEach
    void setup() {
        validator = new QuestionQualityValidator();
    }

    private Question buildValidQuestion() {
        Question q = new Question("D01-1001", "D01",
                "While operating a train between station A and B, the train operator encounters an unexpected automatic brake application under CBTC signalling. In accordance with SOP-04, what action is required first?",
                "EMP001");
        q.setStemMr("स्टेशन A आणि B दरम्यान गाडी चालवताना, ट्रेन ऑपरेटरला अचानक आपत्कालीन ब्रेक लागल्याचे आढळते. नियमांनुसार प्रथम कोणती कृती करावी?");
        q.setSourceRef("SOP-TO-04 Cl. 3.2");
        q.setRationaleEn("The driver must contact the train controller immediately before taking any manual release action.");
        q.setRationaleMr("कोणतीही मॅन्युअल कारवाई करण्यापूर्वी चालकाने ताबडतोब ट्रेन कंट्रोलरशी संपर्क साधला पाहिजे.");
        return q;
    }

    private List<QuestionOption> buildValidOptions() {
        List<QuestionOption> opts = new ArrayList<>();
        opts.add(new QuestionOption(1L, 1, "Inform the OCC controller and confirm the track occupancy status.", "OCC कंट्रोलरला कळवा आणि ट्रॅक स्थितीची खात्री करा.", true, ""));
        opts.add(new QuestionOption(1L, 2, "Release the brake manually and continue driving without permission.", "परवानगीशिवाय मॅन्युअली ब्रेक मोकळा करा आणि गाडी चालवणे सुरू ठेवा.", false, "Bypasses safety interlock without authority"));
        opts.add(new QuestionOption(1L, 3, "Wait inside the cab for ten minutes without notifying passengers.", "प्रवाशांना न कळवता दहा मिनिटे कॅबमध्ये वाट पाहा.", false, "Fails to communicate with passengers or OCC"));
        opts.add(new QuestionOption(1L, 4, "Isolate all brake electronics immediately from the circuit breaker.", "सर्किट ब्रेकरमधून सर्व ब्रेक इलेक्ट्रॉनिक्स तत्काळ वेगळे करा.", false, "Incorrect isolation of healthy subsystems"));
        return opts;
    }

    @Test
    void testValidQuestionPassesWithoutBlocks() {
        Question q = buildValidQuestion();
        List<QuestionOption> opts = buildValidOptions();

        List<QuestionQualityValidator.RuleViolation> violations = validator.validate(q, opts);
        boolean hasBlock = violations.stream().anyMatch(QuestionQualityValidator.RuleViolation::isBlock);
        assertFalse(hasBlock, "Compliant item must not have blocking violations: " + violations);
        assertTrue(q.isNeedsSmeValidation(), "D01 is safety-critical and must require SME validation");
    }

    @Test
    void testR2BlockMultipleQuestionMarks() {
        Question q = buildValidQuestion();
        q.setStemEn("Who is responsible? And what action must be taken immediately?");
        List<QuestionOption> opts = buildValidOptions();

        List<QuestionQualityValidator.RuleViolation> violations = validator.validate(q, opts);
        assertTrue(violations.stream().anyMatch(v -> "R2".equals(v.getRuleId()) && v.isBlock()));
    }

    @Test
    void testR3BlockMissingMisconceptionOnDistractor() {
        Question q = buildValidQuestion();
        List<QuestionOption> opts = buildValidOptions();
        opts.get(1).setMisconception(""); // missing misconception on wrong option

        List<QuestionQualityValidator.RuleViolation> violations = validator.validate(q, opts);
        assertTrue(violations.stream().anyMatch(v -> "R3".equals(v.getRuleId()) && v.isBlock()));
    }

    @Test
    void testR4BlockInsufficientOptions() {
        Question q = buildValidQuestion();
        List<QuestionOption> opts = buildValidOptions().subList(0, 2); // only 2 options

        List<QuestionQualityValidator.RuleViolation> violations = validator.validate(q, opts);
        assertTrue(violations.stream().anyMatch(v -> "R4".equals(v.getRuleId()) && v.isBlock()));
    }

    @Test
    void testR6BlockBannedAllNoneOfTheAbove() {
        Question q = buildValidQuestion();
        List<QuestionOption> opts = buildValidOptions();
        opts.get(3).setTextEn("All of the above are correct procedures.");

        List<QuestionQualityValidator.RuleViolation> violations = validator.validate(q, opts);
        assertTrue(violations.stream().anyMatch(v -> "R6".equals(v.getRuleId()) && v.isBlock()));
    }

    @Test
    void testR9BlockMissingSourceRefOrRationale() {
        Question q = buildValidQuestion();
        q.setSourceRef(""); // empty source ref
        List<QuestionOption> opts = buildValidOptions();

        List<QuestionQualityValidator.RuleViolation> violations = validator.validate(q, opts);
        assertTrue(violations.stream().anyMatch(v -> "R9".equals(v.getRuleId()) && v.isBlock()));
    }

    @Test
    void testR10BlockMissingMarathiText() {
        Question q = buildValidQuestion();
        q.setStemMr(""); // empty Marathi stem
        List<QuestionOption> opts = buildValidOptions();

        List<QuestionQualityValidator.RuleViolation> violations = validator.validate(q, opts);
        assertTrue(violations.stream().anyMatch(v -> "R10".equals(v.getRuleId()) && v.isBlock()));
    }
}
