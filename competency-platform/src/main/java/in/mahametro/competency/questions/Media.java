package in.mahametro.competency.questions;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** An uploaded media file (image, audio, or video) for questions and solutions. */
@Entity
@Table(name = "media")
public class Media {

    public static final String KIND_IMAGE = "image";
    public static final String KIND_AUDIO = "audio";
    public static final String KIND_VIDEO = "video";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String kind;
    private String filename;
    private String storedName;
    private String mime;
    private Long fileSize;
    private String ownerEmpId;
    private LocalDateTime createdAt;

    protected Media() {
    }

    public Media(String kind, String filename, String storedName, String mime, Long fileSize, String ownerEmpId) {
        this.kind = kind;
        this.filename = filename;
        this.storedName = storedName;
        this.mime = mime;
        this.fileSize = fileSize;
        this.ownerEmpId = ownerEmpId;
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

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getStoredName() {
        return storedName;
    }

    public void setStoredName(String storedName) {
        this.storedName = storedName;
    }

    public String getMime() {
        return mime;
    }

    public void setMime(String mime) {
        this.mime = mime;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getOwnerEmpId() {
        return ownerEmpId;
    }

    public void setOwnerEmpId(String ownerEmpId) {
        this.ownerEmpId = ownerEmpId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
