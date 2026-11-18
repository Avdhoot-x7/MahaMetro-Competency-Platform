package in.mahametro.competency.access;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One thing a user may do, for example question.approve. */
@Entity
@Table(name = "app_permission")
public class Permission {

    @Id
    private String code;
    private String description = "";

    protected Permission() {
    }

    public String getCode() { return code; }
    public String getDescription() { return description; }
}
