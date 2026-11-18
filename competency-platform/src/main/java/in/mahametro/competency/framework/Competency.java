package in.mahametro.competency.framework;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One of the 44 competencies. category is Core, Behavioural or Domain. */
@Entity
@Table(name = "competency")
public class Competency {

    public static final String CORE = "Core";
    public static final String BEHAVIOURAL = "Behavioural";
    public static final String DOMAIN = "Domain";

    @Id
    private String code;
    private String nameEn;
    private String nameMr = "";
    private String category;
    private boolean safetyCritical;
    private String descriptionEn = "";
    private String descriptionMr = "";
    private String methods = "";

    protected Competency() {
    }

    public Competency(String code, String nameEn, String category, boolean safetyCritical) {
        this.code = code;
        this.nameEn = nameEn;
        this.category = category;
        this.safetyCritical = safetyCritical;
    }

    public String getCode() { return code; }
    public String getNameEn() { return nameEn; }
    public String getNameMr() { return nameMr; }
    public String getCategory() { return category; }
    public boolean isSafetyCritical() { return safetyCritical; }
    public String getDescriptionEn() { return descriptionEn; }
    public String getDescriptionMr() { return descriptionMr; }
    public String getMethods() { return methods; }
}
