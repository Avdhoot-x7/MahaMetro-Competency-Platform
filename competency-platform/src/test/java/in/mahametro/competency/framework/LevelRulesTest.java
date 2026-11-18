package in.mahametro.competency.framework;

import static org.junit.jupiter.api.Assertions.assertEquals;

import in.mahametro.competency.employees.Grade;
import java.util.List;
import org.junit.jupiter.api.Test;

class LevelRulesTest {

    private final List<CutScore> cuts = List.of(
            new CutScore(1, 0, "Aware"), new CutScore(2, 40, "Basic"), new CutScore(3, 55, "Competent"),
            new CutScore(4, 70, "Advanced"), new CutScore(5, 85, "Expert"));

    @Test
    void scoreBecomesLevel() {
        assertEquals(0, LevelRules.levelFor(null, cuts));
        assertEquals(1, LevelRules.levelFor(39.9, cuts));
        assertEquals(2, LevelRules.levelFor(40.0, cuts));
        assertEquals(3, LevelRules.levelFor(69.9, cuts));
        assertEquals(4, LevelRules.levelFor(70.0, cuts));
        assertEquals(5, LevelRules.levelFor(100.0, cuts));
    }

    @Test
    void gradeShiftsRequiredLevel() {
        Grade operative = new Grade("G1", "Operative", 0, -1, -1);
        Grade middle = new Grade("G4", "Middle Management", 0, 1, 1);
        assertEquals(0, LevelRules.requiredLevel(0, middle, Competency.DOMAIN));
        assertEquals(3, LevelRules.requiredLevel(3, operative, Competency.CORE));
        assertEquals(2, LevelRules.requiredLevel(3, operative, Competency.DOMAIN));
        assertEquals(1, LevelRules.requiredLevel(1, operative, Competency.DOMAIN));
        assertEquals(5, LevelRules.requiredLevel(5, middle, Competency.DOMAIN));
        assertEquals(3, LevelRules.requiredLevel(3, null, Competency.DOMAIN));
    }
}
