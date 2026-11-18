package in.mahametro.competency.scoring;

import java.util.List;

/** A person's full competency profile with the overall Red / Amber / Green readiness. */
public class Profile {

    public static final double AMBER_BELOW_INDEX = 70.0;

    private final List<ProfileRow> rows;
    private final String readiness;
    private final Double index;
    private final int assessed;
    private final int gaps;
    private final int safetyGates;

    public Profile(List<ProfileRow> rows) {
        this.rows = rows;
        int assessedCount = 0;
        int met = 0;
        int gapCount = 0;
        int gates = 0;
        boolean bigGap = false;
        for (ProfileRow row : rows) {
            if (!row.isAssessed()) {
                continue;
            }
            assessedCount++;
            if (row.getGap() == 0) {
                met++;
            } else {
                gapCount++;
                if (row.isSafetyCritical()) {
                    gates++;
                }
                if (row.getGap() >= 2) {
                    bigGap = true;
                }
            }
        }
        this.assessed = assessedCount;
        this.gaps = gapCount;
        this.safetyGates = gates;
        this.index = assessedCount == 0 ? null : Math.round(1000.0 * met / assessedCount) / 10.0;
        if (assessedCount == 0) {
            this.readiness = "Not assessed";
        } else if (gates > 0) {
            this.readiness = "Red";
        } else if (bigGap || this.index < AMBER_BELOW_INDEX) {
            this.readiness = "Amber";
        } else {
            this.readiness = "Green";
        }
    }

    public List<ProfileRow> getRows() { return rows; }
    public String getReadiness() { return readiness; }
    public Double getIndex() { return index; }
    public int getAssessed() { return assessed; }
    public int getGaps() { return gaps; }
    public int getSafetyGates() { return safetyGates; }
}
