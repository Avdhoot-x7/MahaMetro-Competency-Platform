package in.mahametro.competency.questions;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One answer choice of a question. */
@Entity
@Table(name = "question_option")
public class QuestionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long questionId;
    private int ord;
    private String textEn;
    private boolean correct;

    protected QuestionOption() {
    }

    public QuestionOption(Long questionId, int ord, String textEn, boolean correct) {
        this.questionId = questionId;
        this.ord = ord;
        this.textEn = textEn;
        this.correct = correct;
    }

    public Long getId() { return id; }
    public Long getQuestionId() { return questionId; }
    public int getOrd() { return ord; }
    public String getTextEn() { return textEn; }
    public boolean isCorrect() { return correct; }
}
