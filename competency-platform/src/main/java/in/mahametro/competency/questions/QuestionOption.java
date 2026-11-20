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
    private String textMr = "";
    private boolean correct;
    private String misconception = "";
    private Double weight;
    private Integer seqPosition;
    private String matchEn = "";
    private String matchMr = "";

    protected QuestionOption() {
    }

    public QuestionOption(Long questionId, int ord, String textEn, boolean correct) {
        this.questionId = questionId;
        this.ord = ord;
        this.textEn = textEn;
        this.correct = correct;
    }

    public QuestionOption(Long questionId, int ord, String textEn, String textMr, boolean correct, String misconception) {
        this.questionId = questionId;
        this.ord = ord;
        this.textEn = textEn;
        this.textMr = textMr != null ? textMr : "";
        this.correct = correct;
        this.misconception = misconception != null ? misconception : "";
    }

    public QuestionOption(Long id, Long questionId, int ord, String textEn, String textMr, boolean correct, String misconception) {
        this.id = id;
        this.questionId = questionId;
        this.ord = ord;
        this.textEn = textEn;
        this.textMr = textMr != null ? textMr : "";
        this.correct = correct;
        this.misconception = misconception != null ? misconception : "";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getQuestionId() { return questionId; }
    public int getOrd() { return ord; }
    public String getTextEn() { return textEn; }
    public void setTextEn(String textEn) { this.textEn = textEn; }
    public String getTextMr() { return textMr; }
    public void setTextMr(String textMr) { this.textMr = textMr; }
    public boolean isCorrect() { return correct; }
    public String getMisconception() { return misconception; }
    public void setMisconception(String misconception) { this.misconception = misconception; }
    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }
    public Integer getSeqPosition() { return seqPosition; }
    public void setSeqPosition(Integer seqPosition) { this.seqPosition = seqPosition; }
    public String getMatchEn() { return matchEn; }
    public void setMatchEn(String matchEn) { this.matchEn = matchEn; }
    public String getMatchMr() { return matchMr; }
    public void setMatchMr(String matchMr) { this.matchMr = matchMr; }
}
