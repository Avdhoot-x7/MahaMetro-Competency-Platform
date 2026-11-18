package in.mahametro.competency.delivery;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** An assembled question item in a test attempt, tracking the candidate's response. */
@Entity
@Table(name = "attempt_item")
public class AttemptItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long attemptId;
    private Long questionId;
    private int position;
    private String competencyCode;
    private String topicNo = "";
    private String qtype = "single";
    private BigDecimal marks = BigDecimal.ONE;
    private String shuffledOptionIds = "";
    private String selectedOptions = "";
    private String answeredText = "";
    private int timeSpentSeconds = 0;
    private BigDecimal awardedScore;
    private Boolean isCorrect;
    private LocalDateTime answeredAt;

    protected AttemptItem() {
    }

    public AttemptItem(Long attemptId, Long questionId, int position, String competencyCode, String topicNo,
            String qtype, BigDecimal marks, String shuffledOptionIds) {
        this.attemptId = attemptId;
        this.questionId = questionId;
        this.position = position;
        this.competencyCode = competencyCode;
        this.topicNo = topicNo;
        this.qtype = qtype;
        this.marks = marks;
        this.shuffledOptionIds = shuffledOptionIds;
    }

    public Long getId() {
        return id;
    }

    public Long getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(Long attemptId) {
        this.attemptId = attemptId;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public String getCompetencyCode() {
        return competencyCode;
    }

    public void setCompetencyCode(String competencyCode) {
        this.competencyCode = competencyCode;
    }

    public String getTopicNo() {
        return topicNo;
    }

    public void setTopicNo(String topicNo) {
        this.topicNo = topicNo;
    }

    public String getQtype() {
        return qtype;
    }

    public void setQtype(String qtype) {
        this.qtype = qtype;
    }

    public BigDecimal getMarks() {
        return marks;
    }

    public void setMarks(BigDecimal marks) {
        this.marks = marks;
    }

    public String getShuffledOptionIds() {
        return shuffledOptionIds;
    }

    public void setShuffledOptionIds(String shuffledOptionIds) {
        this.shuffledOptionIds = shuffledOptionIds;
    }

    public String getSelectedOptions() {
        return selectedOptions;
    }

    public void setSelectedOptions(String selectedOptions) {
        this.selectedOptions = selectedOptions;
    }

    public String getAnsweredText() {
        return answeredText;
    }

    public void setAnsweredText(String answeredText) {
        this.answeredText = answeredText;
    }

    public int getTimeSpentSeconds() {
        return timeSpentSeconds;
    }

    public void setTimeSpentSeconds(int timeSpentSeconds) {
        this.timeSpentSeconds = timeSpentSeconds;
    }

    public BigDecimal getAwardedScore() {
        return awardedScore;
    }

    public void setAwardedScore(BigDecimal awardedScore) {
        this.awardedScore = awardedScore;
    }

    public Boolean getIsCorrect() {
        return isCorrect;
    }

    public void setIsCorrect(Boolean isCorrect) {
        this.isCorrect = isCorrect;
    }

    public LocalDateTime getAnsweredAt() {
        return answeredAt;
    }

    public void setAnsweredAt(LocalDateTime answeredAt) {
        this.answeredAt = answeredAt;
    }
}
