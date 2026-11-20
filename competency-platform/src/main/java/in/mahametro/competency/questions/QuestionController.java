package in.mahametro.competency.questions;

import in.mahametro.competency.access.AccessService;
import in.mahametro.competency.access.CurrentUser;
import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.framework.CompetencyRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Question bank screens: list, Excel upload for programmers, approval queue for subject experts. */
@Controller
@RequestMapping("/questions")
public class QuestionController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final QuestionRepository questions;
    private final QuestionOptionRepository options;
    private final CompetencyRepository competencies;
    private final QuestionImportService importer;
    private final QuestionWorkflowService workflow;
    private final CurrentUser currentUser;
    private final AccessService access;
    private final AiQuestionGeneratorService generator;
    private final ItemAnalysisService itemAnalysisService;

    public QuestionController(QuestionRepository questions, QuestionOptionRepository options,
            CompetencyRepository competencies, QuestionImportService importer, QuestionWorkflowService workflow,
            CurrentUser currentUser, AccessService access, AiQuestionGeneratorService generator,
            ItemAnalysisService itemAnalysisService) {
        this.questions = questions;
        this.options = options;
        this.competencies = competencies;
        this.importer = importer;
        this.workflow = workflow;
        this.currentUser = currentUser;
        this.access = access;
        this.generator = generator;
        this.itemAnalysisService = itemAnalysisService;
    }

    @GetMapping("/generate")
    public String showGenerator(Principal principal, Model model) {
        currentUser.requireAny(principal, "question.write", "question.manage");
        model.addAttribute("competencies", competencies.findAllByOrderByCodeAsc());
        return "questions/generate";
    }

    @PostMapping("/generate")
    public String runGenerator(@RequestParam("competencyCode") String competencyCode,
                               @RequestParam(name = "targetLevel", defaultValue = "3") int targetLevel,
                               @RequestParam(name = "qtype", defaultValue = "single") String qtype,
                               @RequestParam(name = "nItems", defaultValue = "5") int nItems,
                               @RequestParam("sourceRef") String sourceRef,
                               @RequestParam("referenceText") String referenceText,
                               @RequestParam(name = "misconceptions", defaultValue = "") String misconceptionsText,
                               Principal principal, Model model) {
        Employee me = currentUser.requireAny(principal, "question.write", "question.manage");
        AiQuestionGeneratorService.GenerationRequest req = new AiQuestionGeneratorService.GenerationRequest();
        req.competencyCode = competencyCode;
        req.targetLevel = targetLevel;
        req.qtype = qtype;
        req.nItems = nItems;
        req.sourceRef = sourceRef;
        req.referenceText = referenceText;
        req.authorEmpId = me.getEmpId();
        if (targetLevel <= 1) { req.bloom = "Remember"; req.difficultyBand = "easy"; }
        else if (targetLevel == 2) { req.bloom = "Understand"; req.difficultyBand = "easy"; }
        else if (targetLevel == 3) { req.bloom = "Apply"; req.difficultyBand = "medium"; }
        else { req.bloom = "Analyse"; req.difficultyBand = "hard"; }

        if (misconceptionsText != null && !misconceptionsText.isBlank()) {
            req.misconceptions = java.util.Arrays.stream(misconceptionsText.split("\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        AiQuestionGeneratorService.GeneratedBatchResult batch = generator.generateBatch(req);
        model.addAttribute("batchResult", batch);
        model.addAttribute("competencies", competencies.findAllByOrderByCodeAsc());
        model.addAttribute("message", "Generated " + batch.generatedCount + " items (" + batch.passedCount + " clean, " + batch.warnedCount + " with warnings, " + batch.blockedCount + " blocked by QC).");
        return "questions/generate";
    }

    @GetMapping
    public String list(@RequestParam(name = "status", defaultValue = "") String status,
            @RequestParam(name = "page", defaultValue = "0") int page, Principal principal, Model model) {
        Employee me = currentUser.requireAny(principal, "question.write", "question.approve", "question.manage");
        Pageable pageable = PageRequest.of(Math.max(page, 0), 50, Sort.by(Sort.Direction.DESC, "id"));
        Page<Question> result = status.isEmpty() ? questions.findAll(pageable)
                : questions.findByStatus(status, pageable);
        model.addAttribute("result", result);
        model.addAttribute("status", status);
        model.addAttribute("waiting", questions.countByStatus(Question.SUBMITTED));
        model.addAttribute("inBank", questions.countByStatus(Question.APPROVED));
        model.addAttribute("canUpload", access.hasAny(me.getId(), "question.write"));
        model.addAttribute("canApprove", access.hasAny(me.getId(), "question.approve"));
        return "questions/list";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable("id") Long id, Principal principal, Model model) {
        currentUser.requireAny(principal, "question.write", "question.approve", "question.manage");
        Question question = questions.findById(id).orElseThrow(() -> new AccessDeniedException("No such question"));
        model.addAttribute("v", toView(question));
        return "questions/view";
    }

    @GetMapping("/upload")
    public String uploadForm(Principal principal, Model model) {
        currentUser.requireAny(principal, "question.write");
        model.addAttribute("competencies", competencies.findAll(Sort.by("code")));
        return "questions/upload";
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("competencyCode") String competencyCode,
            @RequestParam("file") MultipartFile file, Principal principal, Model model) {
        Employee me = currentUser.requireAny(principal, "question.write");
        ImportReport report;
        try (InputStream in = file.getInputStream()) {
            report = importer.importFile(me, competencyCode, in);
        } catch (IOException e) {
            report = new ImportReport();
            report.error(0, "The file could not be opened.");
        }
        model.addAttribute("report", report);
        model.addAttribute("competencies", competencies.findAll(Sort.by("code")));
        model.addAttribute("chosen", competencyCode);
        return "questions/upload";
    }

    @GetMapping("/template.xlsx")
    public ResponseEntity<byte[]> template(Principal principal) throws IOException {
        currentUser.requireAny(principal, "question.write");
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Questions");
            Row header = sheet.createRow(0);
            for (int i = 0; i < QuestionImportService.HEADINGS.length; i++) {
                header.createCell(i).setCellValue(QuestionImportService.HEADINGS[i]);
                sheet.setColumnWidth(i, 22 * 256);
            }
            workbook.write(out);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"question-upload-template.xlsx\"")
                    .contentType(MediaType.parseMediaType(XLSX))
                    .body(out.toByteArray());
        }
    }

    /** Questions waiting for this subject expert, 25 at a time, oldest first. */
    @GetMapping("/pending")
    public String pending(Principal principal, Model model) {
        Employee me = currentUser.requireAny(principal, "question.approve");
        List<QuestionView> views = new ArrayList<>();
        Pageable oldestFirst = PageRequest.of(0, 500, Sort.by("id"));
        for (Question question : questions.findByStatus(Question.SUBMITTED, oldestFirst).getContent()) {
            if (views.size() >= 25) {
                break;
            }
            if (!question.getAuthorEmpId().equals(me.getEmpId()) && access.can(me.getId(), "question.approve",
                    QuestionWorkflowService.SCOPE_COMPETENCY, question.getCompetencyCode())) {
                views.add(toView(question));
            }
        }
        model.addAttribute("views", views);
        return "questions/pending";
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable("id") Long id, @RequestParam(name = "note", defaultValue = "") String note,
            Principal principal, RedirectAttributes redirect) {
        Employee me = currentUser.requireAny(principal, "question.approve");
        try {
            Question question = workflow.approve(me, id, note);
            redirect.addFlashAttribute("message", question.getCode() + " approved and added to the bank.");
        } catch (WorkflowException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/questions/pending";
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable("id") Long id, @RequestParam(name = "note", defaultValue = "") String note,
            Principal principal, RedirectAttributes redirect) {
        Employee me = currentUser.requireAny(principal, "question.approve");
        try {
            Question question = workflow.reject(me, id, note);
            redirect.addFlashAttribute("message", question.getCode() + " rejected.");
        } catch (WorkflowException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/questions/pending";
    }

    @GetMapping("/analysis")
    public String showItemAnalysis(Principal principal, Model model) {
        currentUser.requireAny(principal, "question.read", "question.write", "question.approve", "question.manage");
        List<ItemAnalysisService.ItemAnalysisResult> results = itemAnalysisService.runAllItemAnalysis();
        model.addAttribute("results", results);
        long flaggedCount = results.stream().filter(r -> r.getFlags() != null && !r.getFlags().isEmpty() && !r.getFlags().get(0).contains("No test attempts")).count();
        model.addAttribute("flaggedCount", flaggedCount);
        return "questions/analysis";
    }

    @PostMapping("/analysis/run")
    public String runItemAnalysisBatch(Principal principal, RedirectAttributes redirect) {
        currentUser.requireAny(principal, "question.write", "question.approve", "question.manage");
        List<ItemAnalysisService.ItemAnalysisResult> results = itemAnalysisService.runAllItemAnalysis();
        redirect.addFlashAttribute("message", "Item Analysis completed successfully for " + results.size() + " items.");
        return "redirect:/questions/analysis";
    }

    private QuestionView toView(Question question) {
        return new QuestionView(question, options.findByQuestionIdOrderByOrdAsc(question.getId()));
    }
}
