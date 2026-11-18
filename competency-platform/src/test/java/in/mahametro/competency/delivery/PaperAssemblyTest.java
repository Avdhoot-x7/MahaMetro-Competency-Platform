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
import in.mahametro.competency.tests.AssessmentTest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PaperAssemblyTest {

    @Autowired
    PaperAssemblyService paperAssemblyService;

    @Autowired
    QuestionRepository questionRepository;

    @Autowired
    QuestionOptionRepository optionRepository;

    @Autowired
    EmployeeRepository employeeRepository;

    private Employee candidate;

    @BeforeEach
    void setUp() {
        candidate = employeeRepository.save(new Employee("CAND-" + System.nanoTime(), "Test Candidate"));
        candidate.setRoleFamilyCode("SC");
        candidate = employeeRepository.save(candidate);

        // Create 2 variations of topic 101, and 2 variations of topic 102 under competency C01
        createQuestionWithVariation("C01", "101", 1, "Topic 101 Var 1");
        createQuestionWithVariation("C01", "101", 2, "Topic 101 Var 2");
        createQuestionWithVariation("C01", "102", 1, "Topic 102 Var 1");
        createQuestionWithVariation("C01", "102", 2, "Topic 102 Var 2");
    }

    private void createQuestionWithVariation(String comp, String topic, int var, String stem) {
        Question q = new Question(comp + "-" + topic + "-" + var + "-" + System.nanoTime(), comp, stem, "AUTHOR");
        q.setTopicNo(topic);
        q.setVariationNo(var);
        q.setStatus(Question.APPROVED);
        q = questionRepository.save(q);

        optionRepository.save(new QuestionOption(q.getId(), 0, "Opt A", true));
        optionRepository.save(new QuestionOption(q.getId(), 1, "Opt B", false));
    }

    @Test
    void neverPicksTwoVariationsOfSameTopic() {
        AssessmentTest test = new AssessmentTest("TEST-ASY", "Assembly Test", AssessmentTest.KIND_T0, 60);
        test.setItemsPerCompetency(10);
        test.setMaxItems(50);
        test.setShuffleQuestions(true);

        long seed = 123456789L;
        List<AttemptItem> items = paperAssemblyService.assemble(test, candidate, 1L, seed);

        assertFalse(items.isEmpty());

        // Check topic uniqueness per competency
        Set<String> seenCompTopic = new HashSet<>();
        for (AttemptItem item : items) {
            String key = item.getCompetencyCode() + ":" + item.getTopicNo();
            assertFalse(seenCompTopic.contains(key), "Topic variation repeated: " + key);
            seenCompTopic.add(key);
            assertNotNull(item.getShuffledOptionIds());
            assertTrue(item.getShuffledOptionIds().contains(","));
        }
    }

    @Test
    void assemblyIsDeterministicForSameSeed() {
        AssessmentTest test = new AssessmentTest("TEST-DET", "Deterministic Test", AssessmentTest.KIND_T0, 60);
        long seed = 987654321L;

        List<AttemptItem> items1 = paperAssemblyService.assemble(test, candidate, 1L, seed);
        List<AttemptItem> items2 = paperAssemblyService.assemble(test, candidate, 1L, seed);

        assertEquals(items1.size(), items2.size());
        for (int i = 0; i < items1.size(); i++) {
            assertEquals(items1.get(i).getQuestionId(), items2.get(i).getQuestionId());
            assertEquals(items1.get(i).getShuffledOptionIds(), items2.get(i).getShuffledOptionIds());
        }
    }
}
