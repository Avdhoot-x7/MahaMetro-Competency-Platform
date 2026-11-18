package in.mahametro.competency.importers;

import java.util.ArrayList;
import java.util.List;

/**
 * Summary report returned after validating or processing an employee master import file.
 */
public class EmployeeImportReport {

    private int created;
    private int updated;
    private int skipped;
    private boolean dryRun;
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public EmployeeImportReport() {
    }

    public EmployeeImportReport(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public void incrementCreated() {
        this.created++;
    }

    public void incrementUpdated() {
        this.updated++;
    }

    public void incrementSkipped() {
        this.skipped++;
    }

    public void addError(String message) {
        if (message != null && !message.isBlank()) {
            this.errors.add(message.trim());
        }
    }

    public void addWarning(String message) {
        if (message != null && !message.isBlank()) {
            this.warnings.add(message.trim());
        }
    }

    public int getCreated() { return created; }
    public void setCreated(int created) { this.created = created; }

    public int getUpdated() { return updated; }
    public void setUpdated(int updated) { this.updated = updated; }

    public int getSkipped() { return skipped; }
    public void setSkipped(int skipped) { this.skipped = skipped; }

    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }

    public List<String> getErrors() { return errors; }
    public List<String> getWarnings() { return warnings; }
}
