package in.mahametro.competency.framework;

import in.mahametro.competency.employees.Grade;
import java.util.List;

/** Pure rules of the framework: required level for a job, and score to level. Ported from the Python pilot. */
public final class LevelRules {

    private LevelRules() {
    }

    public static int gradeOffset(Grade grade, String category) {
        if (grade == null || category == null) {
            return 0;
        }
        switch (category) {
            case Competency.CORE:
                return grade.getCoreOffset();
            case Competency.BEHAVIOURAL:
                return grade.getBehaviouralOffset();
            case Competency.DOMAIN:
                return grade.getDomainOffset();
            default:
                return 0;
        }
    }

    /** Base level of the role family shifted by the grade, kept between 1 and 5. 0 means not assessed. */
    public static int requiredLevel(int baseLevel, Grade grade, String category) {
        if (baseLevel <= 0) {
            return 0;
        }
        return Math.max(1, Math.min(5, baseLevel + gradeOffset(grade, category)));
    }

    /** Level reached for a percentage score. cuts must be in ascending level order. A missing score gives 0. */
    public static int levelFor(Double score, List<CutScore> cuts) {
        if (score == null) {
            return 0;
        }
        int level = 1;
        for (CutScore cut : cuts) {
            if (score >= cut.getMinScore().doubleValue()) {
                level = cut.getLevelNo();
            }
        }
        return level;
    }
}
