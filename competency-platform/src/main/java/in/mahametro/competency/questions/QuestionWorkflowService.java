package in.mahametro.competency.questions;

import in.mahametro.competency.access.AccessService;
import in.mahametro.competency.core.AuditService;
import in.mahametro.competency.employees.Employee;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The question flow: a programmer uploads (SUBMITTED), a subject expert approves (APPROVED, in the bank)
 * or rejects (REJECTED). There is no step in between. A person cannot approve a question they wrote.
 */
@Service
public class QuestionWorkflowService {

    public static final String SCOPE_COMPETENCY = "COMPETENCY";

    private final QuestionRepository questions;
    private final QuestionHistoryRepository history;
    private final AccessService access;
    private final AuditService audit;

    public QuestionWorkflowService(QuestionRepository questions, QuestionHistoryRepository history,
            AccessService access, AuditService audit) {
        this.questions = questions;
        this.history = history;
        this.access = access;
        this.audit = audit;
    }

    @Transactional
    public Question approve(Employee actor, Long questionId, String note) {
        Question question = loadForDecision(actor, questionId);
        question.setApproverEmpId(actor.getEmpId());
        if (question.getReviewerEmpId() == null || question.getReviewerEmpId().isBlank()) {
            question.setReviewerEmpId(actor.getEmpId());
        }
        question.setApprovedAt(LocalDateTime.now());
        return move(question, actor, Question.APPROVED, note);
    }

    @Transactional
    public Question reject(Employee actor, Long questionId, String note) {
        Question question = loadForDecision(actor, questionId);
        return move(question, actor, Question.REJECTED, note);
    }

    /** Takes an approved question out of use. Past results that used it are kept. */
    @Transactional
    public Question retire(Employee actor, Long questionId, String note) {
        Question question = load(questionId);
        if (!access.can(actor.getId(), "question.manage", SCOPE_COMPETENCY, question.getCompetencyCode())) {
            throw new WorkflowException("You are not allowed to retire questions");
        }
        return move(question, actor, Question.RETIRED, note);
    }

    /** Records that a new question entered the flow. Called by the Excel import. */
    void recordSubmitted(Question question, Employee actor, String note) {
        log(question, actor, "", Question.SUBMITTED, note);
    }

    private Question loadForDecision(Employee actor, Long questionId) {
        Question question = load(questionId);
        if (!Question.SUBMITTED.equals(question.getStatus())) {
            throw new WorkflowException("Question " + question.getCode() + " is " + question.getStatus()
                    + ", so it cannot be decided again");
        }
        if (!access.can(actor.getId(), "question.approve", SCOPE_COMPETENCY, question.getCompetencyCode())) {
            throw new WorkflowException("You are not the subject expert for " + question.getCompetencyCode());
        }
        if (question.getAuthorEmpId().equals(actor.getEmpId())) {
            throw new WorkflowException("You cannot approve or reject a question you wrote");
        }
        return question;
    }

    private Question move(Question question, Employee actor, String toStatus, String note) {
        String fromStatus = question.getStatus();
        question.setStatus(toStatus);
        Question saved = questions.save(question);
        log(saved, actor, fromStatus, toStatus, note);
        return saved;
    }

    private void log(Question question, Employee actor, String fromStatus, String toStatus, String note) {
        history.save(new QuestionHistory(question.getId(), actor.getEmpId(), fromStatus, toStatus, note));
        audit.record(actor.getEmpId(), "question_" + toStatus.toLowerCase(), "question", question.getCode(), note);
    }

    private Question load(Long questionId) {
        return questions.findById(questionId).orElseThrow(() -> new WorkflowException("No such question"));
    }
}
