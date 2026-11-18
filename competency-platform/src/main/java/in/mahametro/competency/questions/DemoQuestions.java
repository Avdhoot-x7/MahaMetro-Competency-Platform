package in.mahametro.competency.questions;

import in.mahametro.competency.employees.Employee;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Three sample questions for the demo data: one already in the bank, two waiting for the subject expert. */
@Component
public class DemoQuestions {

    private static final String SIGNAL_SVG = "<svg width=\"70\" height=\"150\" viewBox=\"0 0 70 150\">"
            + "<rect x=\"10\" y=\"5\" width=\"50\" height=\"140\" rx=\"8\" fill=\"#222\"/>"
            + "<circle cx=\"35\" cy=\"35\" r=\"16\" fill=\"#e53935\"/>"
            + "<circle cx=\"35\" cy=\"75\" r=\"16\" fill=\"#555\"/>"
            + "<circle cx=\"35\" cy=\"115\" r=\"16\" fill=\"#555\"/></svg>";

    private final QuestionRepository questions;
    private final QuestionOptionRepository options;
    private final QuestionWorkflowService workflow;

    public DemoQuestions(QuestionRepository questions, QuestionOptionRepository options,
            QuestionWorkflowService workflow) {
        this.questions = questions;
        this.options = options;
        this.workflow = workflow;
    }

    @Transactional
    public void create(Employee programmer, Employee subjectExpert) {
        Question first = add(programmer, "C01-DEMO-1", "C01", "1", 1,
                "A passenger reports smoke coming from a dustbin on the platform. What do you do first?",
                "Small fires grow fast. Raise the alarm first so help is on the way, then act.",
                new String[] {"Raise the alarm and inform the Station Controller"},
                new String[] {"Look for the nearest fire extinguisher", "Ask the passenger to wait and watch it",
                    "Finish the current task and then check"});
        add(programmer, "C01-DEMO-2", "C01", "2", 1,
                "The signal ahead looks like this:\n" + SIGNAL_SVG + "\nWhat must the train operator do?",
                "A red aspect means stop. The train must not pass the signal without authority.",
                new String[] {"Stop before the signal"},
                new String[] {"Proceed at restricted speed", "Proceed at normal speed", "Sound the horn and proceed"});
        add(programmer, "C01-DEMO-3", "C01", "2", 2,
                "Which of these are part of a correct emergency evacuation? (More than one answer is correct.)",
                "Evacuation uses the marked routes and assembly point; lifts are never used.",
                new String[] {"Guide passengers to the marked exits", "Report to the assembly point"},
                new String[] {"Use the lift to move faster", "Collect personal belongings first"});
        workflow.approve(subjectExpert, first.getId(), "demo data");
    }

    private Question add(Employee author, String code, String competency, String topic, int variation, String text,
            String solution, String[] correct, String[] wrong) {
        Question question = new Question(code, competency, text, author.getEmpId());
        question.setTopicNo(topic);
        question.setVariationNo(variation);
        question.setQuestionType("Text");
        question.setAnswerType(correct.length > 1 ? "Multiple" : "Single");
        question.setQtype(correct.length > 1 ? "multi" : "single");
        question.setSolutionText(solution);
        question.setDifficulty("3");
        question.setUploadBatch("demo");
        question = questions.save(question);
        Question earliest = questions.findFirstByCompetencyCodeAndTopicNoOrderByIdAsc(competency, topic).orElse(null);
        if (earliest != null && !earliest.getId().equals(question.getId())) {
            question.setParentId(earliest.familyId());
            question = questions.save(question);
        }
        int ord = 0;
        for (String choice : correct) {
            options.save(new QuestionOption(question.getId(), ord++, choice, true));
        }
        for (String choice : wrong) {
            options.save(new QuestionOption(question.getId(), ord++, choice, false));
        }
        workflow.recordSubmitted(question, author, "demo data");
        return question;
    }
}
