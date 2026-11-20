package in.mahametro.competency.questions;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates question items against the 10 quality control rules from
 * CoE Question-Writing Guide v1.3 and Question Bank Spec (Nov 18, 2026).
 */
@Component
public class QuestionQualityValidator {

    public static final String SEVERITY_BLOCK = "block";
    public static final String SEVERITY_WARN = "warn";

    private static final List<Pattern> BANNED_PATTERNS = Arrays.asList(
            Pattern.compile("all\\s+of\\s+the\\s+above", Pattern.CASE_INSENSITIVE),
            Pattern.compile("none\\s+of\\s+the\\s+above", Pattern.CASE_INSENSITIVE),
            Pattern.compile("both\\s+[ab]\\s+and\\s+[ab]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("वरील\\s*सर्व", Pattern.CASE_INSENSITIVE),
            Pattern.compile("वरीलपैकी\\s*काहीही\\s*नाही", Pattern.CASE_INSENSITIVE)
    );

    private static final Set<String> ABSOLUTE_WORDS = new HashSet<>(Arrays.asList("always", "never", "only", "all"));
    private static final Set<String> HEDGE_WORDS = new HashSet<>(Arrays.asList("may", "usually", "generally", "often"));

    private static final Set<String> SAFETY_CRITICAL_COMPETENCIES = new HashSet<>(Arrays.asList(
            "C01", "D01", "D02", "D03", "D04", "D05", "D07", "D08", "D10", "D11", "D12", "D13", "D17", "D18"
    ));

    public static class RuleViolation {
        private final String ruleId;
        private final String severity; // "block" or "warn"
        private final String message;

        public RuleViolation(String ruleId, String severity, String message) {
            this.ruleId = ruleId;
            this.severity = severity;
            this.message = message;
        }

        public String getRuleId() { return ruleId; }
        public String getSeverity() { return severity; }
        public String getMessage() { return message; }
        public boolean isBlock() { return SEVERITY_BLOCK.equalsIgnoreCase(severity); }
        public boolean isWarn() { return SEVERITY_WARN.equalsIgnoreCase(severity); }

        @Override
        public String toString() {
            return ruleId + " [" + severity + "]: " + message;
        }
    }

    public List<RuleViolation> validate(Question item, List<QuestionOption> options) {
        List<RuleViolation> violations = new ArrayList<>();

        // Safety-critical check
        if (item.getCompetencyCode() != null && SAFETY_CRITICAL_COMPETENCIES.contains(item.getCompetencyCode().trim())) {
            item.setNeedsSmeValidation(true);
        }

        // R1: Start from real job situation (stem >= 25 words for single/sjt, no "What is"/"Define")
        String stemEn = item.getStemEn() != null ? item.getStemEn().trim() : "";
        int stemWords = stemEn.isEmpty() ? 0 : stemEn.split("\\s+").length;
        String qtype = item.getQtype() != null ? item.getQtype().trim().toLowerCase(Locale.ROOT) : "single";

        if (("single".equals(qtype) || "sjt".equals(qtype)) && stemWords < 25) {
            violations.add(new RuleViolation("R1", SEVERITY_WARN, "Stem has fewer than 25 words (" + stemWords + "). Stems should describe who, where, and what happened."));
        }
        String stemLower = stemEn.toLowerCase(Locale.ROOT);
        if (stemLower.startsWith("what is") || stemLower.startsWith("define") || stemLower.startsWith("which of the following is the definition")) {
            violations.add(new RuleViolation("R1", SEVERITY_WARN, "Stem begins with definitional phrasing. Ground the scenario in an operational job situation."));
        }

        // R2: One point per question (at most one question mark in stem)
        long qmarkCount = stemEn.chars().filter(ch -> ch == '?').count();
        if (qmarkCount > 1) {
            violations.add(new RuleViolation("R2", SEVERITY_BLOCK, "Stem contains multiple question marks (" + qmarkCount + "). Test only one learning point."));
        }

        // R4: Option count
        int optCount = options != null ? options.size() : 0;
        if (("single".equals(qtype) || "sjt".equals(qtype)) && optCount < 4) {
            violations.add(new RuleViolation("R4", SEVERITY_BLOCK, "Requires 4 options for " + qtype + " questions (found " + optCount + ")."));
        } else if ("multi".equals(qtype) && optCount < 5) {
            violations.add(new RuleViolation("R4", SEVERITY_BLOCK, "Requires at least 5 options for multi-select questions (found " + optCount + ")."));
        }

        // R3: Wrong options are real mistakes (every wrong option must state a misconception)
        if (options != null && ("single".equals(qtype) || "multi".equals(qtype) || "sjt".equals(qtype))) {
            for (QuestionOption opt : options) {
                if (!opt.isCorrect()) {
                    String mis = opt.getMisconception() != null ? opt.getMisconception().trim() : "";
                    if (mis.isEmpty()) {
                        violations.add(new RuleViolation("R3", SEVERITY_BLOCK, "Option " + opt.getOrd() + " is incorrect but lacks a documented misconception."));
                    }
                }
            }
        }

        // R5: Similar option length & style (longest / shortest <= 1.5; key is not unique longest)
        if (options != null && options.size() >= 2 && ("single".equals(qtype) || "sjt".equals(qtype))) {
            int minLenEn = Integer.MAX_VALUE;
            int maxLenEn = Integer.MIN_VALUE;
            int keyLenEn = 0;
            boolean hasKey = false;

            for (QuestionOption opt : options) {
                int len = opt.getTextEn() != null ? opt.getTextEn().trim().length() : 0;
                minLenEn = Math.min(minLenEn, len);
                maxLenEn = Math.max(maxLenEn, len);
                if (opt.isCorrect()) {
                    keyLenEn = len;
                    hasKey = true;
                }
            }

            if (minLenEn > 0 && ((double) maxLenEn / minLenEn) > 1.5) {
                violations.add(new RuleViolation("R5", SEVERITY_WARN, "Option length ratio exceeds 1.5 (" + String.format(Locale.ROOT, "%.2f", (double) maxLenEn / minLenEn) + "). Keep option lengths consistent."));
            }
            if (hasKey && keyLenEn == maxLenEn) {
                final int finalMaxLen = maxLenEn;
                long maxCount = options.stream().filter(o -> (o.getTextEn() != null ? o.getTextEn().trim().length() : 0) == finalMaxLen).count();
                if (maxCount == 1) {
                    violations.add(new RuleViolation("R5", SEVERITY_WARN, "The correct option is the unique longest option. Avoid giving away the answer by length."));
                }
            }
        }

        // R6: No all / none of the above
        StringBuilder fullText = new StringBuilder(stemEn);
        if (item.getStemMr() != null) fullText.append(" ").append(item.getStemMr());
        if (options != null) {
            for (QuestionOption opt : options) {
                if (opt.getTextEn() != null) fullText.append(" ").append(opt.getTextEn());
                if (opt.getTextMr() != null) fullText.append(" ").append(opt.getTextMr());
            }
        }
        String combined = fullText.toString();
        for (Pattern p : BANNED_PATTERNS) {
            if (p.matcher(combined).find()) {
                violations.add(new RuleViolation("R6", SEVERITY_BLOCK, "Contains banned phrase matching '" + p.pattern() + "'. Never use all/none of the above."));
            }
        }

        // R7: No giveaway words (always/never in distractors, may/usually only in key)
        if (options != null) {
            for (QuestionOption opt : options) {
                if (!opt.isCorrect() && opt.getTextEn() != null) {
                    for (String w : opt.getTextEn().toLowerCase(Locale.ROOT).split("\\W+")) {
                        if (ABSOLUTE_WORDS.contains(w)) {
                            violations.add(new RuleViolation("R7", SEVERITY_WARN, "Distractor contains absolute giveaway word: '" + w + "'."));
                            break;
                        }
                    }
                } else if (opt.isCorrect() && opt.getTextEn() != null) {
                    for (String w : opt.getTextEn().toLowerCase(Locale.ROOT).split("\\W+")) {
                        if (HEDGE_WORDS.contains(w)) {
                            violations.add(new RuleViolation("R7", SEVERITY_WARN, "Correct key contains hedge giveaway word: '" + w + "'."));
                            break;
                        }
                    }
                }
            }
        }

        // R8: Avoid negative stems (lowercase not / except or multiple NOT)
        long notCount = Pattern.compile("\\bNOT\\b").matcher(stemEn).results().count();
        if (Pattern.compile("\\b(not|except)\\b").matcher(stemEn).find()) {
            violations.add(new RuleViolation("R8", SEVERITY_WARN, "Stem contains lowercase 'not' or 'except'. Prefer positive stems; if unavoidable, write 'NOT' in capitals."));
        }
        if (notCount > 1) {
            violations.add(new RuleViolation("R8", SEVERITY_WARN, "Stem contains multiple negative 'NOT' occurrences. Use a single positive question."));
        }

        // R9: Give source ref and rationale
        String src = item.getSourceRef() != null ? item.getSourceRef().trim() : "";
        String rat = item.getRationaleEn() != null ? item.getRationaleEn().trim() : "";
        if (src.isEmpty()) {
            violations.add(new RuleViolation("R9", SEVERITY_BLOCK, "Missing source reference (SOP, rule-book clause, or manual section)."));
        }
        if (rat.isEmpty()) {
            violations.add(new RuleViolation("R9", SEVERITY_BLOCK, "Missing rationale explaining why the correct answer is right."));
        }

        // R10: Bilingual (Marathi stem and option texts must be filled)
        String stemMr = item.getStemMr() != null ? item.getStemMr().trim() : "";
        if (stemMr.isEmpty()) {
            violations.add(new RuleViolation("R10", SEVERITY_BLOCK, "Missing Marathi question stem (stem_mr). Items must be bilingual."));
        }
        if (options != null) {
            for (QuestionOption opt : options) {
                String tm = opt.getTextMr() != null ? opt.getTextMr().trim() : "";
                if (tm.isEmpty()) {
                    violations.add(new RuleViolation("R10", SEVERITY_BLOCK, "Missing Marathi translation for option " + opt.getOrd() + "."));
                }
            }
        }

        return violations;
    }
}
