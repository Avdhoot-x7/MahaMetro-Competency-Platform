package in.mahametro.competency.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Writes the audit trail. Call it for every change to roles, questions, tests and scores. */
@Service
public class AuditService {

    private final AuditLogRepository logs;

    public AuditService(AuditLogRepository logs) {
        this.logs = logs;
    }

    @Transactional
    public void record(String actor, String action, String entity, Object entityId, String detail) {
        String text = detail == null ? "" : detail;
        if (text.length() > 2000) {
            text = text.substring(0, 2000);
        }
        logs.save(new AuditLog(actor == null ? "" : actor, action, entity, String.valueOf(entityId), text));
    }
}
