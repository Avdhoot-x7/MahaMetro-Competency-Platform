package in.mahametro.competency.delivery;

import in.mahametro.competency.access.CurrentUser;
import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.questions.Question;
import in.mahametro.competency.questions.QuestionOption;
import in.mahametro.competency.questions.QuestionOptionRepository;
import in.mahametro.competency.questions.QuestionRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for the dedicated Formative Practice Drills experience.
 * Untimed, low-stakes self-paced learning with immediate pedagogical feedback,
 * submission at any point, and comprehensive solution ledgers.
 */
@Controller
public class PracticeController {

    public record PracticeSection(
            String key,
            String titleEn,
            String titleMr,
            String icon,
            String descriptionEn,
            String coeResourceUrl,
            long availableQuestions
    ) {}

    public static class QuestionView {
        private final Question question;
        private final List<QuestionOption> options;

        public QuestionView(Question question, List<QuestionOption> options) {
            this.question = question;
            this.options = options;
        }

        public Question getQuestion() { return question; }
        public List<QuestionOption> getOptions() { return options; }
    }

    public static class PracticeResultItem {
        private final Question question;
        private final List<QuestionOption> options;
        private final Long selectedOptionId;
        private final QuestionOption selectedOption;
        private final QuestionOption correctOption;
        private final boolean correct;
        private final boolean attempted;

        public PracticeResultItem(Question question,
                                  List<QuestionOption> options,
                                  Long selectedOptionId,
                                  QuestionOption selectedOption,
                                  QuestionOption correctOption,
                                  boolean correct,
                                  boolean attempted) {
            this.question = question;
            this.options = options;
            this.selectedOptionId = selectedOptionId;
            this.selectedOption = selectedOption;
            this.correctOption = correctOption;
            this.correct = correct;
            this.attempted = attempted;
        }

        public Question getQuestion() { return question; }
        public List<QuestionOption> getOptions() { return options; }
        public Long getSelectedOptionId() { return selectedOptionId; }
        public QuestionOption getSelectedOption() { return selectedOption; }
        public QuestionOption getCorrectOption() { return correctOption; }
        public boolean isCorrect() { return correct; }
        public boolean isAttempted() { return attempted; }
    }

    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository optionRepository;
    private final CurrentUser currentUser;

    public PracticeController(QuestionRepository questionRepository,
                              QuestionOptionRepository optionRepository,
                              CurrentUser currentUser) {
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.currentUser = currentUser;
    }

    public List<PracticeSection> getSections() {
        return List.of(
                new PracticeSection("train_ops",
                        "Train Operations & Cab Safety",
                        "ट्रेन ऑपरेशन्स आणि कॅब सुरक्षा",
                        "🚆",
                        "Cab inspection, ATP/ATO/RM driving modes, emergency braking, speed restriction compliance, and SPAD prevention protocols.",
                        "https://mahametro.org/coe/manuals/train-ops-sop.pdf",
                        questionRepository.countByTopicNoAndStatus("train_ops", "APPROVED")),
                new PracticeSection("station_control",
                        "Station Control & Revenue Operations",
                        "स्टेशन नियंत्रण आणि महसूल कामकाज",
                        "🏢",
                        "Platform crowd surge management, emergency station evacuation, AFC gate troubleshooting, and passenger accessibility.",
                        "https://mahametro.org/coe/manuals/station-control-sop.pdf",
                        questionRepository.countByTopicNoAndStatus("station_control", "APPROVED")),
                new PracticeSection("signaling_telecom",
                        "Signaling, Interlocking & Telecom",
                        "सिग्नलिंग, इंटरलॉकिंग आणि टेलिकॉम",
                        "🚦",
                        "Electronic Interlocking (EI), axle counter reset sequences, point failure crank-handle operations, and radio communication protocol.",
                        "https://mahametro.org/coe/manuals/signaling-telecom-sop.pdf",
                        questionRepository.countByTopicNoAndStatus("signaling_telecom", "APPROVED")),
                new PracticeSection("rolling_stock",
                        "Rolling Stock & Traction Power",
                        "रोलिंग स्टॉक आणि ट्रॅक्शन वीज",
                        "⚡",
                        "Brake system isolation procedures, pantograph/25 kV OHE faults, auxiliary power, door interlocks, and HVAC controls.",
                        "https://mahametro.org/coe/manuals/rolling-stock-sop.pdf",
                        questionRepository.countByTopicNoAndStatus("rolling_stock", "APPROVED")),
                new PracticeSection("safety_fire",
                        "Safety Protocols, Fire & Emergency Response",
                        "सुरक्षा कार्यपद्धती, अग्निशामन व आपत्कालीन कृती",
                        "🧯",
                        "Tunnel fire response, passenger detrainment on viaducts, basic life support / CPR / AED, electrical panel fire, and STOP-work authority.",
                        "https://mahametro.org/coe/manuals/safety-fire-sop.pdf",
                        questionRepository.countByTopicNoAndStatus("safety_fire", "APPROVED")),
                new PracticeSection("workplace_ethics",
                        "Workplace Ethics, PoSH & Conduct",
                        "कामाच्या ठिकाणची नैतिकता, PoSH आणि आचारसंहिता",
                        "🤝",
                        "Prevention of Sexual Harassment (PoSH), anti-corruption, conflict of interest, inclusion, passenger dignity, and whistleblower protections.",
                        "https://mahametro.org/coe/manuals/workplace-ethics-sop.pdf",
                        questionRepository.countByTopicNoAndStatus("workplace_ethics", "APPROVED"))
        );
    }

    @GetMapping("/practice")
    public String practiceHub(Principal principal, Model model) {
        Employee me = currentUser.get(principal);
        model.addAttribute("employee", me);
        model.addAttribute("sections", getSections());
        return "practice/hub";
    }

    @GetMapping("/practice/start")
    public String startPractice(@RequestParam("topic") String topicNo,
                                @RequestParam(value = "limit", defaultValue = "50") int limit,
                                Principal principal,
                                Model model) {
        Employee me = currentUser.get(principal);
        model.addAttribute("employee", me);

        List<PracticeSection> sections = getSections();
        PracticeSection currentSection = sections.stream()
                .filter(s -> s.key().equalsIgnoreCase(topicNo))
                .findFirst()
                .orElse(new PracticeSection(topicNo, "General Metro Operations Practice", "मेट्रो सामान्य सराव", "💡", "Comprehensive formative questions", "", 0));
        model.addAttribute("section", currentSection);

        List<Question> questions = questionRepository.findByTopicNoAndStatus(topicNo, "APPROVED");
        if (questions.isEmpty()) {
            questions = questionRepository.findByTopicNoAndStatus(topicNo, "SUBMITTED");
        }
        if (questions.isEmpty()) {
            questions = questionRepository.findByStatus("APPROVED");
        }

        // Limit to max 50
        if (questions.size() > limit) {
            questions = questions.subList(0, limit);
        }

        List<QuestionView> questionViews = new ArrayList<>();
        for (Question q : questions) {
            List<QuestionOption> options = optionRepository.findByQuestionIdOrderByOrdAsc(q.getId());
            questionViews.add(new QuestionView(q, options));
        }

        model.addAttribute("questions", questionViews);
        model.addAttribute("totalCount", questionViews.size());
        return "practice/player";
    }

    @PostMapping("/practice/submit")
    public String submitPractice(HttpServletRequest request,
                                 @RequestParam("topicNo") String topicNo,
                                 Principal principal,
                                 Model model) {
        Employee me = currentUser.get(principal);
        model.addAttribute("employee", me);

        List<PracticeSection> sections = getSections();
        PracticeSection currentSection = sections.stream()
                .filter(s -> s.key().equalsIgnoreCase(topicNo))
                .findFirst()
                .orElse(new PracticeSection(topicNo, "Metro Operations Practice", "मेट्रो सराव", "💡", "", "", 0));
        model.addAttribute("section", currentSection);

        String[] questionIdParams = request.getParameterValues("questionIds");
        List<PracticeResultItem> resultItems = new ArrayList<>();
        int attemptedCount = 0;
        int correctCount = 0;

        if (questionIdParams != null) {
            for (String qIdStr : questionIdParams) {
                try {
                    Long qId = Long.parseLong(qIdStr.trim());
                    Question q = questionRepository.findById(qId).orElse(null);
                    if (q == null) continue;

                    List<QuestionOption> options = optionRepository.findByQuestionIdOrderByOrdAsc(q.getId());
                    String selectedParam = request.getParameter("answer_" + qId);

                    Long selectedOptionId = null;
                    QuestionOption selectedOption = null;
                    boolean isAttempted = (selectedParam != null && !selectedParam.isBlank());

                    if (isAttempted) {
                        try {
                            selectedOptionId = Long.parseLong(selectedParam.trim());
                            Long finalSelectedId = selectedOptionId;
                            selectedOption = options.stream()
                                    .filter(opt -> opt.getId().equals(finalSelectedId))
                                    .findFirst()
                                    .orElse(null);
                            attemptedCount++;
                        } catch (NumberFormatException ignored) {}
                    }

                    QuestionOption correctOption = options.stream()
                            .filter(QuestionOption::isCorrect)
                            .findFirst()
                            .orElse(null);

                    boolean isCorrect = false;
                    if (selectedOption != null && correctOption != null && selectedOption.getId().equals(correctOption.getId())) {
                        isCorrect = true;
                        correctCount++;
                    }

                    resultItems.add(new PracticeResultItem(
                            q,
                            options,
                            selectedOptionId,
                            selectedOption,
                            correctOption,
                            isCorrect,
                            isAttempted
                    ));
                } catch (NumberFormatException ignored) {}
            }
        }

        int totalCount = resultItems.size();
        int incorrectCount = attemptedCount - correctCount;
        int unattemptedCount = totalCount - attemptedCount;
        double scorePct = totalCount > 0 ? (correctCount * 100.0 / totalCount) : 0.0;
        double accuracyPct = attemptedCount > 0 ? (correctCount * 100.0 / attemptedCount) : 0.0;

        String performanceBand;
        String bandColor;
        if (scorePct >= 80.0) {
            performanceBand = "High Proficiency (Mastered)";
            bandColor = "#059669";
        } else if (scorePct >= 60.0) {
            performanceBand = "Developing (Approaching Standard)";
            bandColor = "#d97706";
        } else {
            performanceBand = "Needs Reinforcement (Review Recommended)";
            bandColor = "#dc2626";
        }

        model.addAttribute("resultItems", resultItems);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("attemptedCount", attemptedCount);
        model.addAttribute("correctCount", correctCount);
        model.addAttribute("incorrectCount", incorrectCount);
        model.addAttribute("unattemptedCount", unattemptedCount);
        model.addAttribute("scorePct", String.format(Locale.US, "%.1f", scorePct));
        model.addAttribute("accuracyPct", String.format(Locale.US, "%.1f", accuracyPct));
        model.addAttribute("performanceBand", performanceBand);
        model.addAttribute("bandColor", bandColor);

        return "practice/result";
    }
}
