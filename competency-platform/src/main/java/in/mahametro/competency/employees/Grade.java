package in.mahametro.competency.employees;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Grade band G1 to G5. The offsets raise or lower the required level for each competency category. */
@Entity
@Table(name = "grade")
public class Grade {

    @Id
    private String code;
    private String bandEn;
    private String bandMr = "";
    private int coreOffset;
    private int behaviouralOffset;
    private int domainOffset;

    protected Grade() {
    }

    public Grade(String code, String bandEn, int coreOffset, int behaviouralOffset, int domainOffset) {
        this.code = code;
        this.bandEn = bandEn;
        this.coreOffset = coreOffset;
        this.behaviouralOffset = behaviouralOffset;
        this.domainOffset = domainOffset;
    }

    public String getCode() { return code; }
    public String getBandEn() { return bandEn; }
    public String getBandMr() { return bandMr; }
    public int getCoreOffset() { return coreOffset; }
    public int getBehaviouralOffset() { return behaviouralOffset; }
    public int getDomainOffset() { return domainOffset; }
}
