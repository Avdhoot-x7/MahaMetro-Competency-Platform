package in.mahametro.competency.access;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;

/** A named set of permissions, for example reviewer. */
@Entity
@Table(name = "app_role")
public class Role {

    public static final String SUPERADMIN = "superadmin";

    @Id
    private String code;
    private String nameEn;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "role_permission",
            joinColumns = @JoinColumn(name = "role_code"),
            inverseJoinColumns = @JoinColumn(name = "permission_code"))
    private Set<Permission> permissions = new HashSet<>();

    protected Role() {
    }

    public String getCode() { return code; }
    public String getNameEn() { return nameEn; }
    public Set<Permission> getPermissions() { return permissions; }

    public boolean hasPermission(String permissionCode) {
        for (Permission p : permissions) {
            if (p.getCode().equals(permissionCode)) {
                return true;
            }
        }
        return false;
    }
}
