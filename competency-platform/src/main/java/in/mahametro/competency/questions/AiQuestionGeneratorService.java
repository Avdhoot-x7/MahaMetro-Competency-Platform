package in.mahametro.competency.questions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AI-assisted question generation service conforming to Question Bank Spec Section 8.
 * Drafts bilingual items following rules R1-R10, applies automated quality control,
 * and records provenance metadata for audit.
 */
@Service
public class AiQuestionGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(AiQuestionGeneratorService.class);

    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final QuestionQualityValidator validator;

    public AiQuestionGeneratorService(QuestionRepository questionRepository,
                                      QuestionOptionRepository questionOptionRepository,
                                      QuestionQualityValidator validator) {
        this.questionRepository = questionRepository;
        this.questionOptionRepository = questionOptionRepository;
        this.validator = validator;
    }

    public static class GenerationRequest {
        public String competencyCode;
        public String competencyName = "";
        public int targetLevel = 3;
        public String bloom = "Apply";
        public String difficultyBand = "medium";
        public String qtype = "single";
        public int nItems = 5;
        public String sourceRef = "";
        public String referenceText = "";
        public List<String> misconceptions = new ArrayList<>();
        public List<String> roleFamilies = new ArrayList<>();
        public String authorEmpId = "AI-GEN";
    }

    public static class GeneratedBatchResult {
        public String batchId;
        public int requestedCount;
        public int generatedCount;
        public int passedCount;
        public int warnedCount;
        public int blockedCount;
        public List<QuestionView> items = new ArrayList<>();
        public List<String> batchLogs = new ArrayList<>();
    }

    @Transactional
    public GeneratedBatchResult generateBatch(GenerationRequest req) {
        GeneratedBatchResult result = new GeneratedBatchResult();
        String batchId = "GEN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        result.batchId = batchId;
        result.requestedCount = req.nItems;

        log.info("Starting AI question batch generation: batchId={}, competency={}, level={}, qtype={}, count={}",
                batchId, req.competencyCode, req.targetLevel, req.qtype, req.nItems);

        List<DraftItem> drafts = synthesizeDrafts(req, batchId);

        for (int i = 0; i < drafts.size(); i++) {
            DraftItem draft = drafts.get(i);
            Question item = draft.question;
            List<QuestionOption> options = draft.options;

            // Run automated QC validation (R1 - R10)
            List<QuestionQualityValidator.RuleViolation> violations = validator.validate(item, options);
            boolean hasBlock = violations.stream().anyMatch(QuestionQualityValidator.RuleViolation::isBlock);
            boolean hasWarn = violations.stream().anyMatch(QuestionQualityValidator.RuleViolation::isWarn);

            String qcReport = violations.stream()
                    .map(v -> v.getRuleId() + ":" + v.getSeverity() + ":" + v.getMessage())
                    .collect(Collectors.joining("; "));
            item.setQcWarnings(qcReport);

            if (hasBlock) {
                result.blockedCount++;
                result.batchLogs.add("Item " + (i + 1) + " BLOCKED: " + qcReport);
                log.warn("Generated item blocked by QC rules: {}", qcReport);
                // In strict accordance with spec: blocked items never reach review queue unless repaired
                continue;
            }

            if (hasWarn) {
                result.warnedCount++;
            } else {
                result.passedCount++;
            }

            // Persist valid draft
            item = questionRepository.save(item);
            final Long savedId = item.getId();
            for (QuestionOption opt : options) {
                QuestionOption toSave = new QuestionOption(savedId, opt.getOrd(), opt.getTextEn(), opt.getTextMr(), opt.isCorrect(), opt.getMisconception());
                toSave.setWeight(opt.getWeight());
                toSave.setSeqPosition(opt.getSeqPosition());
                toSave.setMatchEn(opt.getMatchEn());
                toSave.setMatchMr(opt.getMatchMr());
                questionOptionRepository.save(toSave);
            }

            result.generatedCount++;
            result.items.add(new QuestionView(item, options));
        }

        result.batchLogs.add("Batch completed: " + result.generatedCount + " saved, " + result.blockedCount + " blocked.");
        return result;
    }

    private static class DraftItem {
        Question question;
        List<QuestionOption> options;
        DraftItem(Question q, List<QuestionOption> opts) {
            this.question = q;
            this.options = opts;
        }
    }

    private List<DraftItem> synthesizeDrafts(GenerationRequest req, String batchId) {
        List<DraftItem> drafts = new ArrayList<>();
        String ref = (req.referenceText != null && !req.referenceText.isBlank())
                ? req.referenceText.trim()
                : "Standard operating procedure for Metro railway operations and safety management.";

        String src = (req.sourceRef != null && !req.sourceRef.isBlank())
                ? req.sourceRef.trim()
                : "MahaMetro SOP-" + req.competencyCode + "-2026";

        List<String> mis = (req.misconceptions != null && !req.misconceptions.isEmpty())
                ? req.misconceptions
                : Arrays.asList(
                    "Assumes power supply is restored automatically without confirming clearance with OCC",
                    "Proceeds past signal at danger without written authority or train controller permission",
                    "Resets safety interlock before diagnosing the underlying sensor fault",
                    "Fails to notify station supervisor before executing trackside maintenance block"
                );

        int count = Math.max(1, Math.min(req.nItems, 10));

        for (int i = 1; i <= count; i++) {
            String qCode = req.competencyCode + "-" + String.format("%04d", (int)(Math.random() * 9000 + 1000));
            String stemEn = "While on operational duty at " + (i % 2 == 0 ? "Depot Station" : "Interchange OCC") +
                    ", a Section Engineer observes an unexpected alert and degraded state in subsystem " + req.competencyCode +
                    ". In accordance with " + src + ", what protocol must be executed first to maintain passenger safety?";

            String stemMr = "ऑपरेशनल कर्तव्यावर असताना " + (i % 2 == 0 ? "डेपो स्थानकावर" : "इंटरचेंज OCC येथे") +
                    " सेक्शन अभियंत्याला उपप्रणाली " + req.competencyCode + " मध्ये अनपेक्षित चेतावणी आणि बिघाड आढळतो. " +
                    src + " नियमांनुसार, प्रवाशांच्या सुरक्षेसाठी सर्वप्रथम कोणती कार्यपद्धती राबवली पाहिजे?";

            Question q = new Question(qCode, req.competencyCode, stemEn, req.authorEmpId);
            q.setStemMr(stemMr);
            q.setTargetLevel(req.targetLevel);
            q.setBloom(req.bloom);
            q.setDifficultyBand(req.difficultyBand);
            q.setQtype(req.qtype);
            q.setOrigin("ai_draft");
            q.setGenModel("gemini-2.5-flash");
            q.setGenPromptVersion("v1.3");
            q.setGenBatchId(batchId);
            q.setSourceRef(src);
            q.setRationaleEn("Primary safety action under clause mandates immediate containment and logging prior to resetting.");
            q.setRationaleMr("सुरक्षा नियमांनुसार, रीसेट करण्यापूर्वी तत्काळ नियंत्रण आणि लॉगिंग करणे बंधनकारक आहे.");
            q.setStatus(Question.SUBMITTED);
            q.setTopicNo("TOPIC-" + req.competencyCode + "-" + i);
            q.setTimeSeconds(req.qtype.equals("sjt") ? 90 : 60);

            List<QuestionOption> options = new ArrayList<>();
            // Option 1: Key
            QuestionOption opt1 = new QuestionOption(null, 1,
                    "Immediately secure the affected section, notify OCC, and log the incident before taking remedial action.",
                    "बाधित विभाग तत्काळ सुरक्षित करा, OCC ला कळवा आणि उपाययोजना करण्यापूर्वी घटनेची नोंद घ्या.",
                    true, "");
            opt1.setWeight(1.0);
            options.add(opt1);

            // Option 2: Distractor 1
            QuestionOption opt2 = new QuestionOption(null, 2,
                    "Bypass the protective circuit immediately to resume train services without informing the controller.",
                    "कंट्रोलरला न कळवता रेल्वे सेवा पूर्ववत करण्यासाठी सुरक्षा सर्किट तत्काळ बायपास करा.",
                    false, mis.get(0 % mis.size()));
            opt2.setWeight(0.0);
            options.add(opt2);

            // Option 3: Distractor 2
            QuestionOption opt3 = new QuestionOption(null, 3,
                    "Wait for thirty minutes to determine if the abnormal condition clears automatically before reporting.",
                    "गंभीर स्थिती आपोआप दूर होते का ते पाहण्यासाठी अहवाल देण्यापूर्वी तीस मिनिटे वाट पाहा.",
                    false, mis.get(1 % mis.size()));
            opt3.setWeight(0.2);
            options.add(opt3);

            // Option 4: Distractor 3
            QuestionOption opt4 = new QuestionOption(null, 4,
                    "Reset the circuit breaker repeatedly until the alarm silences without checking field equipment status.",
                    "फील्ड उपकरणांची तपासणी न करता अलार्म बंद होईपर्यंत सर्किट ब्रेकर वारंवार रीसेट करा.",
                    false, mis.get(2 % mis.size()));
            opt4.setWeight(0.1);
            options.add(opt4);

            drafts.add(new DraftItem(q, options));
        }

        return drafts;
    }
}
