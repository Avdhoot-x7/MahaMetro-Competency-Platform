package in.mahametro.competency.scoring;

import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.employees.Grade;
import in.mahametro.competency.employees.GradeRepository;
import in.mahametro.competency.employees.RoleFamily;
import in.mahametro.competency.employees.RoleFamilyRepository;
import in.mahametro.competency.framework.Competency;
import in.mahametro.competency.framework.CompetencyRepository;
import in.mahametro.competency.framework.CutScore;
import in.mahametro.competency.framework.CutScoreRepository;
import in.mahametro.competency.framework.LevelRules;
import in.mahametro.competency.framework.RoleProfile;
import in.mahametro.competency.framework.RoleProfileRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds competency profiles: required level from the job, composite score from the measurements,
 * level from the cut scores, gap and readiness. Ported from the Python pilot.
 */
@Service
public class ProfileService {

    /** Share of each measurement method in the composite score, by job group. */
    private static final Map<String, Map<String, Integer>> WEIGHTS = Map.of(
            "OPS", Map.of("knowledge", 35, "sjt", 20, "practical", 35, "rating", 10),
            "MNT", Map.of("knowledge", 40, "sjt", 15, "practical", 35, "rating", 10),
            "PJS", Map.of("knowledge", 45, "sjt", 30, "practical", 10, "rating", 15),
            "NT", Map.of("knowledge", 45, "sjt", 30, "practical", 10, "rating", 15),
            "BEHAVIOURAL", Map.of("knowledge", 0, "sjt", 60, "practical", 0, "rating", 40));

    private static final List<String> CYCLE_ORDER = List.of("T0", "T1", "T2", "T3", "EVENT");
    private static final List<String> CATEGORY_ORDER =
            List.of(Competency.CORE, Competency.BEHAVIOURAL, Competency.DOMAIN);

    private final CompetencyRepository competencies;
    private final GradeRepository grades;
    private final RoleFamilyRepository roleFamilies;
    private final RoleProfileRepository roleProfiles;
    private final CutScoreRepository cutScores;
    private final CompetencyScoreRepository scores;

    public ProfileService(CompetencyRepository competencies, GradeRepository grades,
            RoleFamilyRepository roleFamilies, RoleProfileRepository roleProfiles, CutScoreRepository cutScores,
            CompetencyScoreRepository scores) {
        this.competencies = competencies;
        this.grades = grades;
        this.roleFamilies = roleFamilies;
        this.roleProfiles = roleProfiles;
        this.cutScores = cutScores;
        this.scores = scores;
    }

    @Transactional(readOnly = true)
    public Profile build(Employee employee) {
        return buildAll(List.of(employee)).get(employee.getId());
    }

    /** Profiles for many employees with the framework loaded once. Key is the employee's database id. */
    @Transactional(readOnly = true)
    public Map<Long, Profile> buildAll(List<Employee> employees) {
        Map<Long, Profile> result = new HashMap<>();
        if (employees.isEmpty()) {
            return result;
        }
        Map<String, Competency> competencyByCode = new HashMap<>();
        for (Competency c : competencies.findAll()) {
            competencyByCode.put(c.getCode(), c);
        }
        Map<String, Grade> gradeByCode = new HashMap<>();
        for (Grade g : grades.findAll()) {
            gradeByCode.put(g.getCode(), g);
        }
        Map<String, String> groupByFamily = new HashMap<>();
        for (RoleFamily f : roleFamilies.findAll()) {
            groupByFamily.put(f.getCode(), f.getJobGroup());
        }
        List<CutScore> cuts = cutScores.findAllByOrderByLevelNoAsc();
        Map<String, List<RoleProfile>> profileByFamily = new HashMap<>();

        // employee id -> competency -> cycle -> method -> score (a later record replaces an earlier one)
        Map<Long, Map<String, Map<String, Map<String, Double>>>> measured = new HashMap<>();
        List<Long> ids = new ArrayList<>();
        for (Employee e : employees) {
            ids.add(e.getId());
        }
        for (CompetencyScore s : scores.findByEmployeeIdInOrderByIdAsc(ids)) {
            measured.computeIfAbsent(s.getEmployeeId(), k -> new HashMap<>())
                    .computeIfAbsent(s.getCompetencyCode(), k -> new HashMap<>())
                    .computeIfAbsent(s.getCycle(), k -> new HashMap<>())
                    .put(s.getMethod(), s.getScore().doubleValue());
        }

        for (Employee e : employees) {
            List<ProfileRow> rows = new ArrayList<>();
            String family = e.getRoleFamilyCode();
            if (family != null) {
                List<RoleProfile> needs = profileByFamily.computeIfAbsent(family, roleProfiles::findByRoleFamilyCode);
                Grade grade = e.getGradeCode() == null ? null : gradeByCode.get(e.getGradeCode());
                String group = groupByFamily.getOrDefault(family, "NT");
                Map<String, Map<String, Map<String, Double>>> mine = measured.getOrDefault(e.getId(), Map.of());
                for (RoleProfile need : needs) {
                    Competency c = competencyByCode.get(need.getCompetencyCode());
                    if (c == null || need.getBaseLevel() <= 0) {
                        continue;
                    }
                    int required = LevelRules.requiredLevel(need.getBaseLevel(), grade, c.getCategory());
                    Map<String, Integer> weights = Competency.BEHAVIOURAL.equals(c.getCategory())
                            ? WEIGHTS.get("BEHAVIOURAL") : WEIGHTS.getOrDefault(group, WEIGHTS.get("NT"));
                    Double score = latestComposite(mine.get(c.getCode()), weights);
                    rows.add(new ProfileRow(c.getCode(), c.getNameEn(), c.getCategory(), c.isSafetyCritical(),
                            required, score, LevelRules.levelFor(score, cuts)));
                }
                rows.sort(Comparator.comparingInt((ProfileRow r) -> CATEGORY_ORDER.indexOf(r.getCategory()))
                        .thenComparing(ProfileRow::getCode));
            }
            result.put(e.getId(), new Profile(rows));
        }
        return result;
    }

    /** Composite score of the most recent cycle that has any measurement, or null if there is none. */
    private static Double latestComposite(Map<String, Map<String, Double>> byCycle, Map<String, Integer> weights) {
        if (byCycle == null || byCycle.isEmpty()) {
            return null;
        }
        String latest = null;
        for (String cycle : byCycle.keySet()) {
            if (latest == null || CYCLE_ORDER.indexOf(cycle) > CYCLE_ORDER.indexOf(latest)) {
                latest = cycle;
            }
        }
        return composite(byCycle.get(latest), weights);
    }

    /** Weighted average of the methods that were measured; the share of a missing method is spread over the rest. */
    static Double composite(Map<String, Double> byMethod, Map<String, Integer> weights) {
        double total = 0;
        double weightSum = 0;
        for (Map.Entry<String, Double> entry : byMethod.entrySet()) {
            int weight = weights.getOrDefault(entry.getKey(), 0);
            total += entry.getValue() * weight;
            weightSum += weight;
        }
        if (weightSum == 0) {
            // only methods with no weight were measured (for example a knowledge test on a behavioural competency)
            if (byMethod.isEmpty()) {
                return null;
            }
            for (double value : byMethod.values()) {
                total += value;
            }
            return Math.round(10.0 * total / byMethod.size()) / 10.0;
        }
        return Math.round(10.0 * total / weightSum) / 10.0;
    }
}
