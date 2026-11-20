package in.mahametro.competency.delivery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Micro-interaction event recorded during an assessment attempt or solution review.
 * Captures navigation, dwell times, answer revisions, and solution exploration.
 */
@Entity
@Table(name = "attempt_event")
public class AttemptEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long attemptId;
    private Long itemId;
    private String kind;

    @Column(name = "`value`")
    private String value = "";

    @Column(name = "`at`")
    private LocalDateTime at;

    protected AttemptEvent() {
    }

    public AttemptEvent(Long attemptId, Long itemId, String kind, String value) {
        this.attemptId = attemptId;
        this.itemId = itemId;
        this.kind = kind;
        this.value = value != null ? value : "";
    }

    public AttemptEvent(Long attemptId, Long itemId, String kind, String value, LocalDateTime at) {
        this.attemptId = attemptId;
        this.itemId = itemId;
        this.kind = kind;
        this.value = value != null ? value : "";
        this.at = at;
    }

    @PrePersist
    void onCreate() {
        if (at == null) {
            at = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Long getAttemptId() { return attemptId; }
    public Long getItemId() { return itemId; }
    public String getKind() { return kind; }
    public String getValue() { return value; }
    public LocalDateTime getAt() { return at; }
}
