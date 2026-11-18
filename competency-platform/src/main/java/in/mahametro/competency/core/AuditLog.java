package in.mahametro.competency.core;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** One line of the audit trail: who did what to which record. */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDateTime at = LocalDateTime.now();
    private String actor = "";
    private String action;
    private String entity = "";
    private String entityId = "";
    private String detail = "";

    protected AuditLog() {
    }

    public AuditLog(String actor, String action, String entity, String entityId, String detail) {
        this.actor = actor;
        this.action = action;
        this.entity = entity;
        this.entityId = entityId;
        this.detail = detail;
    }

    public Long getId() { return id; }
    public LocalDateTime getAt() { return at; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getEntity() { return entity; }
    public String getEntityId() { return entityId; }
    public String getDetail() { return detail; }
}
