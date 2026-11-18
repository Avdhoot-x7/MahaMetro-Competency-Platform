package in.mahametro.competency.questions;

import java.util.ArrayList;
import java.util.List;

/** Result of one Excel upload: how many questions were added and which rows were refused, with the reason. */
public class ImportReport {

    private int created;
    private final List<String> errors = new ArrayList<>();

    void added() {
        created++;
    }

    void error(int line, String message) {
        errors.add(line > 0 ? "Row " + line + ": " + message : message);
    }

    public int getCreated() { return created; }
    public List<String> getErrors() { return errors; }
}
