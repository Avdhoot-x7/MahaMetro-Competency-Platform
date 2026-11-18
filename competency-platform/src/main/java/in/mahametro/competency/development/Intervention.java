package in.mahametro.competency.development;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A training course, simulator module, lab practical or e-learning intervention
 * in the Maha Metro training catalogue.
 */
@Entity
@Table(name = "intervention")
public class Intervention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String competencyCode;
    private String category;
    private String gapType; // safety | major | minor | behavioural | strength
    private String titleEn;
    private String titleMr = "";
    private String provider = "CoE"; // CoE | Simulator | CoE Lab | iGOT Karmayogi | OEM | OJT
    private String mode = "Classroom"; // Classroom | Simulator | Lab practical | Workshop | E-learning | Mentoring
    private BigDecimal durationHours = BigDecimal.valueOf(4.0);
    private String link = "";
    private LocalDateTime createdAt;

    protected Intervention() {
    }

    public Intervention(String competencyCode, String category, String gapType, String titleEn, String provider,
                        String mode, BigDecimal durationHours) {
        this.competencyCode = competencyCode;
        this.category = category;
        this.gapType = gapType;
        this.titleEn = titleEn;
        this.provider = provider;
        this.mode = mode;
        this.durationHours = durationHours;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public String getCompetencyCode() { return competencyCode; }
    public void setCompetencyCode(String competencyCode) { this.competencyCode = competencyCode; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getGapType() { return gapType; }
    public void setGapType(String gapType) { this.gapType = gapType; }
    public String getTitleEn() { return titleEn; }
    public void setTitleEn(String titleEn) { this.titleEn = titleEn; }
    public String getTitleMr() { return titleMr; }
    public void setTitleMr(String titleMr) { this.titleMr = titleMr; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public BigDecimal getDurationHours() { return durationHours; }
    public void setDurationHours(BigDecimal durationHours) { this.durationHours = durationHours; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
