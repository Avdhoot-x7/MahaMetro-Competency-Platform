package in.mahametro.competency.proctoring;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Audit log of a single proctoring event during a test attempt
 * (tab switch, window blur, full-screen change, copy-paste attempt, or webcam snapshot).
 */
@Entity
@Table(name = "proctor_event")
public class ProctorEvent {

    public static final String KIND_TAB_HIDDEN = "tab_hidden";
    public static final String KIND_TAB_VISIBLE = "tab_visible";
    public static final String KIND_FULLSCREEN_EXIT = "fullscreen_exit";
    public static final String KIND_COPY = "copy";
    public static final String KIND_PASTE = "paste";
    public static final String KIND_CONTEXT_MENU = "context_menu";
    public static final String KIND_BLUR = "blur";
    public static final String KIND_SNAPSHOT = "snapshot";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long attemptId;
    private String kind;
    private String detail = "";
    private Long mediaId;
    private LocalDateTime createdAt;

    protected ProctorEvent() {
    }

    public ProctorEvent(Long attemptId, String kind, String detail) {
        this.attemptId = attemptId;
        this.kind = kind;
        this.detail = detail == null ? "" : detail;
    }

    public ProctorEvent(Long attemptId, String kind, String detail, Long mediaId) {
        this(attemptId, kind, detail);
        this.mediaId = mediaId;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getAttemptId() {
        return attemptId;
    }

    public String getKind() {
        return kind;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Long getMediaId() {
        return mediaId;
    }

    public void setMediaId(Long mediaId) {
        this.mediaId = mediaId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
