package in.mahametro.competency.questions;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** One status change of a question, with who did it and their comment. */
@Entity
@Table(name = "question_history")
public class QuestionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long questionId;
    private LocalDateTime at = LocalDateTime.now();
    private String actor;
    private String fromStatus;
    private String toStatus;
    private String note = "";

    protected QuestionHistory() {
    }

    public QuestionHistory(Long questionId, String actor, String fromStatus, String toStatus, String note) {
        this.questionId = questionId;
        this.actor = actor;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.note = note == null ? "" : note;
    }

    public Long getId() { return id; }
    public Long getQuestionId() { return questionId; }
    public LocalDateTime getAt() { return at; }
    public String getActor() { return actor; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public String getNote() { return note; }
}
