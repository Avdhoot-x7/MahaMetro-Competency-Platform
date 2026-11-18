package in.mahametro.competency.tests;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** An assessment test configuration, schedule window, target criteria, and status. */
@Entity
@Table(name = "test")
public class AssessmentTest {

    public static final String KIND_PRACTICE = "PRACTICE";
    public static final String KIND_T0 = "T0";
    public static final String KIND_T1 = "T1";
    public static final String KIND_T2 = "T2";
    public static final String KIND_T3 = "T3";

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_CLOSED = "CLOSED";

    public static final String TARGET_ALL = "ALL";
    public static final String TARGET_ROLE_FAMILY = "ROLE_FAMILY";
    public static final String TARGET_GRADE = "GRADE";
    public static final String TARGET_DEPARTMENT = "DEPARTMENT";
    public static final String TARGET_EMPLOYEE = "EMPLOYEE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;
    private String titleEn;
    private String titleMr = "";
    private String instructionsEn = "";
    private String instructionsMr = "";
    private String kind = KIND_T0;
    private Long blueprintId;
    private int itemsPerCompetency = 3;
    private int maxItems = 80;
    private int durationMinutes = 90;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private int maxAttempts = 1;
    private boolean shuffleQuestions = true;
    private boolean shuffleOptions = true;
    private boolean showFeedback = false;
    private boolean allowBack = true;
    private String targetType = TARGET_ALL;
    private String targetValue = "";
    private String status = STATUS_DRAFT;
    private String createdBy = "";
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected AssessmentTest() {
    }

    public AssessmentTest(String code, String titleEn, String kind, int durationMinutes) {
        this.code = code;
        this.titleEn = titleEn;
        this.kind = kind;
        this.durationMinutes = durationMinutes;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isPractice() {
        return KIND_PRACTICE.equalsIgnoreCase(kind);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTitleEn() {
        return titleEn;
    }

    public void setTitleEn(String titleEn) {
        this.titleEn = titleEn;
    }

    public String getTitleMr() {
        return titleMr;
    }

    public void setTitleMr(String titleMr) {
        this.titleMr = titleMr;
    }

    public String getInstructionsEn() {
        return instructionsEn;
    }

    public void setInstructionsEn(String instructionsEn) {
        this.instructionsEn = instructionsEn;
    }

    public String getInstructionsMr() {
        return instructionsMr;
    }

    public void setInstructionsMr(String instructionsMr) {
        this.instructionsMr = instructionsMr;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public Long getBlueprintId() {
        return blueprintId;
    }

    public void setBlueprintId(Long blueprintId) {
        this.blueprintId = blueprintId;
    }

    public int getItemsPerCompetency() {
        return itemsPerCompetency;
    }

    public void setItemsPerCompetency(int itemsPerCompetency) {
        this.itemsPerCompetency = itemsPerCompetency;
    }

    public int getMaxItems() {
        return maxItems;
    }

    public void setMaxItems(int maxItems) {
        this.maxItems = maxItems;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public void setEndAt(LocalDateTime endAt) {
        this.endAt = endAt;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public boolean isShuffleQuestions() {
        return shuffleQuestions;
    }

    public void setShuffleQuestions(boolean shuffleQuestions) {
        this.shuffleQuestions = shuffleQuestions;
    }

    public boolean isShuffleOptions() {
        return shuffleOptions;
    }

    public void setShuffleOptions(boolean shuffleOptions) {
        this.shuffleOptions = shuffleOptions;
    }

    public boolean isShowFeedback() {
        return showFeedback;
    }

    public void setShowFeedback(boolean showFeedback) {
        this.showFeedback = showFeedback;
    }

    public boolean isAllowBack() {
        return allowBack;
    }

    public void setAllowBack(boolean allowBack) {
        this.allowBack = allowBack;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetValue() {
        return targetValue;
    }

    public void setTargetValue(String targetValue) {
        this.targetValue = targetValue;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
