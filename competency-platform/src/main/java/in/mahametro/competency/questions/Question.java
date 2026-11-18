package in.mahametro.competency.questions;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * One question in the bank. Questions of the same competency with the same topic number are variations
 * of each other: parentId points to the first of them, and a test uses at most one per candidate.
 */
@Entity
@Table(name = "question")
public class Question {

    public static final String SUBMITTED = "SUBMITTED";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String RETIRED = "RETIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String code;
    private Long parentId;
    private Long assignmentId;
    private String competencyCode;
    private String topicNo = "";
    private int variationNo = 1;
    private String questionType = "";
    private String answerType = "";
    private int targetLevel = 3;
    private String bloom = "Understand";
    private String qtype = "single";
    private String stemEn;
    private String stemMr = "";
    private String mediaRef = "";
    private String solutionText = "";
    private String solutionMedia = "";
    private int timeSeconds = 60;
    private String difficulty = "";
    private String pool = "exam";
    private String status = SUBMITTED;
    private String authorEmpId;
    private String reviewerEmpId = "";
    private String approverEmpId = "";
    private LocalDateTime approvedAt;
    private String uploadBatch = "";
    private int versionNo = 1;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected Question() {
    }

    public Question(String code, String competencyCode, String stemEn, String authorEmpId) {
        this.code = code;
        this.competencyCode = competencyCode;
        this.stemEn = stemEn;
        this.authorEmpId = authorEmpId;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** The id shared by all variations of one topic. */
    public Long familyId() {
        return parentId != null ? parentId : id;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    void setCode(String code) { this.code = code; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Long getAssignmentId() { return assignmentId; }
    public String getCompetencyCode() { return competencyCode; }
    public String getTopicNo() { return topicNo; }
    public void setTopicNo(String topicNo) { this.topicNo = topicNo; }
    public int getVariationNo() { return variationNo; }
    public void setVariationNo(int variationNo) { this.variationNo = variationNo; }
    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public String getAnswerType() { return answerType; }
    public void setAnswerType(String answerType) { this.answerType = answerType; }
    public int getTargetLevel() { return targetLevel; }
    public void setTargetLevel(int targetLevel) { this.targetLevel = targetLevel; }
    public String getBloom() { return bloom; }
    public String getQtype() { return qtype; }
    public void setQtype(String qtype) { this.qtype = qtype; }
    public String getStemEn() { return stemEn; }
    public void setStemEn(String stemEn) { this.stemEn = stemEn; }
    public String getStemMr() { return stemMr; }
    public String getMediaRef() { return mediaRef; }
    public void setMediaRef(String mediaRef) { this.mediaRef = mediaRef; }
    public String getSolutionText() { return solutionText; }
    public void setSolutionText(String solutionText) { this.solutionText = solutionText; }
    public String getSolutionMedia() { return solutionMedia; }
    public void setSolutionMedia(String solutionMedia) { this.solutionMedia = solutionMedia; }
    public int getTimeSeconds() { return timeSeconds; }
    public void setTimeSeconds(int timeSeconds) { this.timeSeconds = timeSeconds; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getPool() { return pool; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAuthorEmpId() { return authorEmpId; }
    public String getApproverEmpId() { return approverEmpId; }
    void setApproverEmpId(String approverEmpId) { this.approverEmpId = approverEmpId; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public String getUploadBatch() { return uploadBatch; }
    public void setUploadBatch(String uploadBatch) { this.uploadBatch = uploadBatch; }
    public int getVersionNo() { return versionNo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
