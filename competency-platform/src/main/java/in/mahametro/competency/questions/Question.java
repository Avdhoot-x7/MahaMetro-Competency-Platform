package in.mahametro.competency.questions;

import jakarta.persistence.Column;
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

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_REVIEW = "SUBMITTED";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_RETIRED = "RETIRED";

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
    private String approverEmpId = "";
    private LocalDateTime approvedAt;
    private String uploadBatch = "";
    private int versionNo = 1;

    // Spec extensions: Nov 18, 2026 Question Bank Generation & Assessment Spec
    private String difficultyBand = "medium";
    private String origin = "sme";
    private String genModel = "";
    private String genPromptVersion = "";
    private String genBatchId = "";
    private String qcWarnings = "";
    private String sourceRef = "";
    private String rationaleEn = "";
    private String rationaleMr = "";
    private Double answerNumeric;
    private Double numericTolerance;
    private String acceptedAnswers = "";
    private String rubricEn = "";
    private String rubricMr = "";
    private boolean needsSmeValidation = false;
    private Double pValue;
    private Double pointBiserial;
    @Column(name = "stats_n")
    private int statsN = 0;

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
    public void setId(Long id) { this.id = id; }
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
    public void setBloom(String bloom) { this.bloom = bloom; }
    public String getQtype() { return qtype; }
    public void setQtype(String qtype) { this.qtype = qtype; }
    public String getStemEn() { return stemEn; }
    public void setStemEn(String stemEn) { this.stemEn = stemEn; }
    public String getStemMr() { return stemMr; }
    public void setStemMr(String stemMr) { this.stemMr = stemMr; }
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
    public void setApproverEmpId(String approverEmpId) { this.approverEmpId = approverEmpId; }
    public String getReviewerEmpId() { return approverEmpId; }
    public void setReviewerEmpId(String reviewerEmpId) { this.approverEmpId = reviewerEmpId; }
    public String getUploadBatch() { return uploadBatch; }
    public void setUploadBatch(String uploadBatch) { this.uploadBatch = uploadBatch; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public int getVersionNo() { return versionNo; }
    public void setVersionNo(int versionNo) { this.versionNo = versionNo; }
    public String getDifficultyBand() { return difficultyBand; }
    public void setDifficultyBand(String difficultyBand) { this.difficultyBand = difficultyBand; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getGenModel() { return genModel; }
    public void setGenModel(String genModel) { this.genModel = genModel; }
    public String getGenPromptVersion() { return genPromptVersion; }
    public void setGenPromptVersion(String genPromptVersion) { this.genPromptVersion = genPromptVersion; }
    public String getGenBatchId() { return genBatchId; }
    public void setGenBatchId(String genBatchId) { this.genBatchId = genBatchId; }
    public String getQcWarnings() { return qcWarnings; }
    public void setQcWarnings(String qcWarnings) { this.qcWarnings = qcWarnings; }
    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }
    public String getRationaleEn() { return rationaleEn; }
    public void setRationaleEn(String rationaleEn) { this.rationaleEn = rationaleEn; }
    public String getRationaleMr() { return rationaleMr; }
    public void setRationaleMr(String rationaleMr) { this.rationaleMr = rationaleMr; }
    public Double getAnswerNumeric() { return answerNumeric; }
    public void setAnswerNumeric(Double answerNumeric) { this.answerNumeric = answerNumeric; }
    public Double getNumericTolerance() { return numericTolerance; }
    public void setNumericTolerance(Double numericTolerance) { this.numericTolerance = numericTolerance; }
    public String getAcceptedAnswers() { return acceptedAnswers; }
    public void setAcceptedAnswers(String acceptedAnswers) { this.acceptedAnswers = acceptedAnswers; }
    public String getRubricEn() { return rubricEn; }
    public void setRubricEn(String rubricEn) { this.rubricEn = rubricEn; }
    public String getRubricMr() { return rubricMr; }
    public void setRubricMr(String rubricMr) { this.rubricMr = rubricMr; }
    public boolean isNeedsSmeValidation() { return needsSmeValidation; }
    public void setNeedsSmeValidation(boolean needsSmeValidation) { this.needsSmeValidation = needsSmeValidation; }
    public Double getPValue() { return pValue; }
    public void setPValue(Double pValue) { this.pValue = pValue; }
    public Double getPointBiserial() { return pointBiserial; }
    public void setPointBiserial(Double pointBiserial) { this.pointBiserial = pointBiserial; }
    public int getStatsN() { return statsN; }
    public void setStatsN(int statsN) { this.statsN = statsN; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
