package in.mahametro.competency.reporting;

import java.util.ArrayList;
import java.util.List;

/**
 * High-level aggregated statistics and slice breakdowns for Supervisor, HoD, and Executive dashboards.
 */
public class DashboardSummary {

    private int totalPeople;
    private int assessedPeople;
    private double coveragePct;
    private int redCount;
    private int amberCount;
    private int greenCount;
    private int notAssessedCount;
    private int safetyGates;
    private Double averageIndexPct;
    private List<TopGapDTO> topGaps = new ArrayList<>();
    private List<BreakdownRow> breakdowns = new ArrayList<>();

    public DashboardSummary() {
    }

    public static class TopGapDTO {
        private String code;
        private String nameEn;
        private String category;
        private boolean safetyCritical;
        private int gapCount;

        public TopGapDTO(String code, String nameEn, String category, boolean safetyCritical, int gapCount) {
            this.code = code;
            this.nameEn = nameEn;
            this.category = category;
            this.safetyCritical = safetyCritical;
            this.gapCount = gapCount;
        }

        public String getCode() { return code; }
        public String getNameEn() { return nameEn; }
        public String getCategory() { return category; }
        public boolean isSafetyCritical() { return safetyCritical; }
        public int getGapCount() { return gapCount; }
    }

    public static class BreakdownRow {
        private String key;
        private String label;
        private int total;
        private int assessed;
        private int red;
        private int amber;
        private int green;
        private Double avgIndex;

        public BreakdownRow(String key, String label, int total, int assessed, int red, int amber, int green, Double avgIndex) {
            this.key = key;
            this.label = label;
            this.total = total;
            this.assessed = assessed;
            this.red = red;
            this.amber = amber;
            this.green = green;
            this.avgIndex = avgIndex;
        }

        public String getKey() { return key; }
        public String getLabel() { return label; }
        public int getTotal() { return total; }
        public int getAssessed() { return assessed; }
        public int getRed() { return red; }
        public int getAmber() { return amber; }
        public int getGreen() { return green; }
        public Double getAvgIndex() { return avgIndex; }
    }

    public int getTotalPeople() { return totalPeople; }
    public void setTotalPeople(int totalPeople) { this.totalPeople = totalPeople; }

    public int getAssessedPeople() { return assessedPeople; }
    public void setAssessedPeople(int assessedPeople) { this.assessedPeople = assessedPeople; }

    public double getCoveragePct() { return coveragePct; }
    public void setCoveragePct(double coveragePct) { this.coveragePct = coveragePct; }

    public int getRedCount() { return redCount; }
    public void setRedCount(int redCount) { this.redCount = redCount; }

    public int getAmberCount() { return amberCount; }
    public void setAmberCount(int amberCount) { this.amberCount = amberCount; }

    public int getGreenCount() { return greenCount; }
    public void setGreenCount(int greenCount) { this.greenCount = greenCount; }

    public int getNotAssessedCount() { return notAssessedCount; }
    public void setNotAssessedCount(int notAssessedCount) { this.notAssessedCount = notAssessedCount; }

    public int getSafetyGates() { return safetyGates; }
    public void setSafetyGates(int safetyGates) { this.safetyGates = safetyGates; }

    public Double getAverageIndexPct() { return averageIndexPct; }
    public void setAverageIndexPct(Double averageIndexPct) { this.averageIndexPct = averageIndexPct; }

    public List<TopGapDTO> getTopGaps() { return topGaps; }
    public void setTopGaps(List<TopGapDTO> topGaps) { this.topGaps = topGaps; }

    public List<BreakdownRow> getBreakdowns() { return breakdowns; }
    public void setBreakdowns(List<BreakdownRow> breakdowns) { this.breakdowns = breakdowns; }
}
