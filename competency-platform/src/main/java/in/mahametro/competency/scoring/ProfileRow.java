package in.mahametro.competency.scoring;

/** One competency in a person's profile: what the job needs, what they scored, and the gap. */
public class ProfileRow {

    private final String code;
    private final String name;
    private final String category;
    private final boolean safetyCritical;
    private final int required;
    private final Double score;
    private final int level;
    private final int gap;
    private final String status;

    public ProfileRow(String code, String name, String category, boolean safetyCritical, int required,
            Double score, int level) {
        this.code = code;
        this.name = name;
        this.category = category;
        this.safetyCritical = safetyCritical;
        this.required = required;
        this.score = score;
        this.level = level;
        this.gap = score == null ? 0 : Math.max(0, required - level);
        if (score == null) {
            this.status = "Not assessed";
        } else if (gap == 0) {
            this.status = "Meets";
        } else if (safetyCritical) {
            this.status = "Safety gate";
        } else if (gap >= 2) {
            this.status = "Priority";
        } else {
            this.status = "Developing";
        }
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getNameEn() { return name; }
    public String getCategory() { return category; }
    public boolean isSafetyCritical() { return safetyCritical; }
    public int getRequired() { return required; }
    public Double getScore() { return score; }
    public int getLevel() { return level; }
    public int getGap() { return gap; }
    public String getStatus() { return status; }
    public boolean isAssessed() { return score != null; }
}
