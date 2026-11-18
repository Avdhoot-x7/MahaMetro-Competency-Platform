package in.mahametro.competency.access;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** A role given to an employee, either everywhere (scope ALL) or for one department, competency, test and so on. */
@Entity
@Table(name = "employee_role")
public class EmployeeRole {

    public static final String SCOPE_ALL = "ALL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long employeeId;
    private String roleCode;
    private String scopeType = SCOPE_ALL;
    private String scopeValue = "";
    private String grantedBy = "";
    private LocalDateTime grantedAt = LocalDateTime.now();

    protected EmployeeRole() {
    }

    public EmployeeRole(Long employeeId, String roleCode, String scopeType, String scopeValue, String grantedBy) {
        this.employeeId = employeeId;
        this.roleCode = roleCode;
        this.scopeType = scopeType;
        this.scopeValue = scopeValue;
        this.grantedBy = grantedBy;
    }

    public Long getId() { return id; }
    public Long getEmployeeId() { return employeeId; }
    public String getRoleCode() { return roleCode; }
    public String getScopeType() { return scopeType; }
    public String getScopeValue() { return scopeValue; }
    public String getGrantedBy() { return grantedBy; }
    public LocalDateTime getGrantedAt() { return grantedAt; }
}
