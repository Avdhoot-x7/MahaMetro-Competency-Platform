package in.mahametro.competency.employees;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One of the 16 job families, for example Train Operations. jobGroup is OPS, MNT, PJS or NT. */
@Entity
@Table(name = "role_family")
public class RoleFamily {

    @Id
    private String code;
    private String nameEn;
    private String nameMr = "";
    private String jobGroup = "NT";

    protected RoleFamily() {
    }

    public RoleFamily(String code, String nameEn, String jobGroup) {
        this.code = code;
        this.nameEn = nameEn;
        this.jobGroup = jobGroup;
    }

    public String getCode() { return code; }
    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }
    public String getNameMr() { return nameMr; }
    public void setNameMr(String nameMr) { this.nameMr = nameMr; }
    public String getJobGroup() { return jobGroup; }
    public void setJobGroup(String jobGroup) { this.jobGroup = jobGroup; }
}
