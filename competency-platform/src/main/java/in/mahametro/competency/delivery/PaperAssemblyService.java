package in.mahametro.competency.delivery;

import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.framework.RoleProfile;
import in.mahametro.competency.framework.RoleProfileRepository;
import in.mahametro.competency.questions.Question;
import in.mahametro.competency.questions.QuestionOption;
import in.mahametro.competency.questions.QuestionOptionRepository;
import in.mahametro.competency.questions.QuestionRepository;
import in.mahametro.competency.tests.AssessmentTest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Builds a deterministic test paper for a candidate.
 * Strictly guarantees that no candidate ever receives more than one variation of the same topic.
 */
@Service
public class PaperAssemblyService {

    private final QuestionRepository questions;
    private final QuestionOptionRepository options;
    private final RoleProfileRepository roleProfiles;

    public PaperAssemblyService(QuestionRepository questions, QuestionOptionRepository options,
            RoleProfileRepository roleProfiles) {
        this.questions = questions;
        this.options = options;
        this.roleProfiles = roleProfiles;
    }

    public List<AttemptItem> assemble(AssessmentTest test, Employee employee, Long attemptId, long seed) {
        Random rng = new Random(seed);

        // Determine target competencies for candidate
        List<String> targetCompetencies = new ArrayList<>();
        if (employee.getRoleFamilyCode() != null && !employee.getRoleFamilyCode().isBlank()) {
            for (RoleProfile rp : roleProfiles.findByRoleFamilyCode(employee.getRoleFamilyCode())) {
                targetCompetencies.add(rp.getCompetencyCode());
            }
        }

        // If candidate's role has no profile or bank is generic, draw from any approved questions
        if (targetCompetencies.isEmpty()) {
            for (Question q : questions.findByStatus(Question.APPROVED)) {
                if (!targetCompetencies.contains(q.getCompetencyCode())) {
                    targetCompetencies.add(q.getCompetencyCode());
                }
            }
        }

        List<Question> chosenQuestions = new ArrayList<>();

        for (String compCode : targetCompetencies) {
            List<Question> bank = questions.findByCompetencyCodeAndStatus(compCode, Question.APPROVED);
            if (bank.isEmpty()) {
                continue;
            }

            // Group by topicNo to guarantee: at most ONE variation per (competencyCode, topicNo)
            Map<String, List<Question>> byTopic = new LinkedHashMap<>();
            for (Question q : bank) {
                String topicKey = q.getTopicNo() == null ? "" : q.getTopicNo();
                byTopic.computeIfAbsent(topicKey, k -> new ArrayList<>()).add(q);
            }

            // From each topic group, pick at most one variation using the seed
            List<Question> topicPicks = new ArrayList<>();
            for (List<Question> variations : byTopic.values()) {
                if (variations.size() == 1) {
                    topicPicks.add(variations.get(0));
                } else {
                    int idx = rng.nextInt(variations.size());
                    topicPicks.add(variations.get(idx));
                }
            }

            if (test.isShuffleQuestions()) {
                Collections.shuffle(topicPicks, rng);
            }

            // Cap items per competency
            int perComp = Math.min(topicPicks.size(), test.getItemsPerCompetency());
            for (int i = 0; i < perComp; i++) {
                chosenQuestions.add(topicPicks.get(i));
            }
        }

        // Overall test question shuffling and max items cap
        if (test.isShuffleQuestions()) {
            Collections.shuffle(chosenQuestions, rng);
        }
        if (chosenQuestions.size() > test.getMaxItems()) {
            chosenQuestions = chosenQuestions.subList(0, test.getMaxItems());
        }

        // Build AttemptItem list with position and shuffled options
        List<AttemptItem> items = new ArrayList<>();
        int position = 1;
        for (Question q : chosenQuestions) {
            List<QuestionOption> optList = options.findByQuestionIdOrderByOrdAsc(q.getId());
            List<Long> optIds = optList.stream().map(QuestionOption::getId).collect(Collectors.toList());
            if (test.isShuffleOptions()) {
                Collections.shuffle(optIds, rng);
            }
            String shuffledIds = optIds.stream().map(String::valueOf).collect(Collectors.joining(","));

            AttemptItem item = new AttemptItem(
                    attemptId,
                    q.getId(),
                    position++,
                    q.getCompetencyCode(),
                    q.getTopicNo(),
                    q.getQtype(),
                    BigDecimal.ONE,
                    shuffledIds
            );
            items.add(item);
        }

        return items;
    }
}
