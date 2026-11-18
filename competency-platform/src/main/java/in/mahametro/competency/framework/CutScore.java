package in.mahametro.competency.framework;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Lowest percentage score that earns a level. */
@Entity
@Table(name = "cut_score")
public class CutScore {

    @Id
    private Integer levelNo;
    @Column(precision = 5, scale = 2)
    private BigDecimal minScore;
    private String labelEn;
    private String labelMr = "";

    protected CutScore() {
    }

    public CutScore(int levelNo, double minScore, String labelEn) {
        this.levelNo = levelNo;
        this.minScore = BigDecimal.valueOf(minScore);
        this.labelEn = labelEn;
    }

    public Integer getLevelNo() { return levelNo; }
    public BigDecimal getMinScore() { return minScore; }
    public void setMinScore(BigDecimal minScore) { this.minScore = minScore; }
    public String getLabelEn() { return labelEn; }
    public String getLabelMr() { return labelMr; }
}
