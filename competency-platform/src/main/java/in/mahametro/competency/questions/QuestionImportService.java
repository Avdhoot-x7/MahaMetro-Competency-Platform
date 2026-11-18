package in.mahametro.competency.questions;

import in.mahametro.competency.access.AccessService;
import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.employees.EmployeeRepository;
import in.mahametro.competency.framework.CompetencyRepository;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads questions from the Excel template and puts them in the flow as SUBMITTED.
 * Good rows are added; a row with a problem is skipped and listed in the report.
 */
@Service
public class QuestionImportService {

    /** Column headings of the upload template, in order. */
    public static final String[] HEADINGS = {
        "Sr. No", "Question Type", "Answer Type", "Topic Number", "Question (Text Only)",
        "Correct Answer 1", "Correct Answer 2", "Correct Answer 3", "Correct Answer 4",
        "Wrong Answer 1", "Wrong Answer 2", "Wrong Answer 3", "Time in seconds", "Difficulty Level",
        "Question (Image/ Audio/ Video)", "Contributor's Registered mailId", "Solution (Text Only)",
        "Solution (Image/ Audio/ Video)", "Variation Number"};

    /** Short keys the headings are recognised by, after removing spaces, punctuation and capitals. */
    private static final String[] KEYS = {
        "srno", "questiontype", "answertype", "topicnumber", "questiontext",
        "correctanswer1", "correctanswer2", "correctanswer3", "correctanswer4",
        "wronganswer1", "wronganswer2", "wronganswer3", "timeinseconds", "difficultylevel",
        "questionimage", "contributor", "solutiontext", "solutionimage", "variationnumber"};

    private final QuestionRepository questions;
    private final QuestionOptionRepository options;
    private final CompetencyRepository competencies;
    private final EmployeeRepository employees;
    private final AccessService access;
    private final QuestionWorkflowService workflow;
    private final DataFormatter formatter = new DataFormatter();

    public QuestionImportService(QuestionRepository questions, QuestionOptionRepository options,
            CompetencyRepository competencies, EmployeeRepository employees, AccessService access,
            QuestionWorkflowService workflow) {
        this.questions = questions;
        this.options = options;
        this.competencies = competencies;
        this.employees = employees;
        this.access = access;
        this.workflow = workflow;
    }

    @Transactional
    public ImportReport importFile(Employee uploader, String competencyCode, InputStream file) {
        ImportReport report = new ImportReport();
        if (competencyCode == null || !competencies.existsById(competencyCode)) {
            report.error(0, "Choose a competency for these questions.");
            return report;
        }
        if (!access.can(uploader.getId(), "question.write", QuestionWorkflowService.SCOPE_COMPETENCY,
                competencyCode)) {
            report.error(0, "You are not allowed to upload questions for " + competencyCode + ".");
            return report;
        }
        String batch = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())
                + "-" + uploader.getEmpId();
        if (batch.length() > 40) {
            batch = batch.substring(0, 40);
        }
        Workbook workbook;
        try {
            workbook = WorkbookFactory.create(file);
        } catch (Exception e) {
            report.error(0, "The file could not be read. Upload an Excel .xlsx file made from the template.");
            return report;
        }
        try (Workbook wb = workbook) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            Map<String, Integer> columns = header == null ? new HashMap<>() : readHeader(header);
            if (!columns.containsKey("questiontext") || !columns.containsKey("topicnumber")
                    || !columns.containsKey("correctanswer1")) {
                report.error(0, "The first row must hold the column headings of the template.");
                return report;
            }
            Set<String> seen = new HashSet<>();
            for (int r = header.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row != null) {
                    importRow(row, r + 1, columns, competencyCode, uploader, batch, seen, report);
                }
            }
        } catch (java.io.IOException e) {
            report.error(0, "The file could not be closed properly.");
        }
        return report;
    }

    private void importRow(Row row, int line, Map<String, Integer> columns, String competencyCode,
            Employee uploader, String batch, Set<String> seen, ImportReport report) {
        String text = get(row, columns, "questiontext");
        List<String> correct = values(row, columns, "correctanswer1", "correctanswer2", "correctanswer3",
                "correctanswer4");
        List<String> wrong = values(row, columns, "wronganswer1", "wronganswer2", "wronganswer3");
        if (text.isEmpty() && correct.isEmpty() && wrong.isEmpty()) {
            return; // empty row
        }
        String topic = get(row, columns, "topicnumber");
        int variation = Math.max(1, number(get(row, columns, "variationnumber"), 1));
        String solution = get(row, columns, "solutiontext");
        if (text.isEmpty()) {
            report.error(line, "the question text is empty.");
            return;
        }
        if (text.length() > 4000 || solution.length() > 4000) {
            report.error(line, "the question or solution text is longer than 4000 characters.");
            return;
        }
        if (topic.isEmpty() || topic.length() > 20) {
            report.error(line, "the topic number is missing or too long.");
            return;
        }
        if (correct.isEmpty()) {
            report.error(line, "there is no correct answer.");
            return;
        }
        if (correct.size() + wrong.size() < 2) {
            report.error(line, "a question needs at least two answer choices.");
            return;
        }
        for (String choice : correct) {
            if (choice.length() > 2000) {
                report.error(line, "an answer is longer than 2000 characters.");
                return;
            }
        }
        for (String choice : wrong) {
            if (choice.length() > 2000) {
                report.error(line, "an answer is longer than 2000 characters.");
                return;
            }
        }
        if (!seen.add(topic + "|" + variation)) {
            report.error(line, "topic " + topic + " variation " + variation + " appears twice in this file.");
            return;
        }
        if (questions.existsByCompetencyCodeAndTopicNoAndVariationNoAndStatusNot(competencyCode, topic,
                variation, Question.REJECTED)) {
            report.error(line, "topic " + topic + " variation " + variation + " is already in the bank.");
            return;
        }
        Employee author = uploader;
        String email = get(row, columns, "contributor");
        if (!email.isEmpty()) {
            Optional<Employee> contributor = employees.findFirstByEmailIgnoreCase(email);
            if (contributor.isEmpty()) {
                report.error(line, "no employee is registered with the e-mail " + email + ".");
                return;
            }
            author = contributor.get();
        }

        Question question = new Question("TMP-" + UUID.randomUUID().toString().replace("-", ""),
                competencyCode, text, author.getEmpId());
        question.setTopicNo(topic);
        question.setVariationNo(variation);
        question.setQuestionType(limit(get(row, columns, "questiontype"), 40));
        question.setAnswerType(limit(get(row, columns, "answertype"), 40));
        question.setQtype(correct.size() > 1 ? "multi" : "single");
        int seconds = number(get(row, columns, "timeinseconds"), 60);
        question.setTimeSeconds(seconds > 0 ? seconds : 60);
        String difficulty = limit(get(row, columns, "difficultylevel"), 20);
        question.setDifficulty(difficulty);
        int level = number(difficulty, 0);
        if (level >= 1 && level <= 5) {
            question.setTargetLevel(level);
        }
        question.setMediaRef(limit(get(row, columns, "questionimage"), 500));
        question.setSolutionText(solution);
        question.setSolutionMedia(limit(get(row, columns, "solutionimage"), 500));
        question.setUploadBatch(batch);
        question = questions.save(question);
        question.setCode(competencyCode + "-" + String.format("%06d", question.getId()));
        Optional<Question> first = questions.findFirstByCompetencyCodeAndTopicNoOrderByIdAsc(competencyCode, topic);
        if (first.isPresent() && !first.get().getId().equals(question.getId())) {
            question.setParentId(first.get().familyId());
        }
        question = questions.save(question);

        int ord = 0;
        for (String choice : correct) {
            options.save(new QuestionOption(question.getId(), ord++, choice, true));
        }
        for (String choice : wrong) {
            options.save(new QuestionOption(question.getId(), ord++, choice, false));
        }
        workflow.recordSubmitted(question, uploader, "uploaded in " + batch);
        report.added();
    }

    private Map<String, Integer> readHeader(Row header) {
        Map<String, Integer> columns = new HashMap<>();
        for (Cell cell : header) {
            String heading = formatter.formatCellValue(cell).toLowerCase().replaceAll("[^a-z0-9]", "");
            for (String key : KEYS) {
                if (heading.startsWith(key) && !columns.containsKey(key)) {
                    columns.put(key, cell.getColumnIndex());
                    break;
                }
            }
        }
        return columns;
    }

    private String get(Row row, Map<String, Integer> columns, String key) {
        Integer index = columns.get(key);
        if (index == null) {
            return "";
        }
        Cell cell = row.getCell(index);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private List<String> values(Row row, Map<String, Integer> columns, String... keys) {
        List<String> found = new ArrayList<>();
        for (String key : keys) {
            String value = get(row, columns, key);
            if (!value.isEmpty()) {
                found.add(value);
            }
        }
        return found;
    }

    private static int number(String text, int fallback) {
        try {
            return (int) Math.round(Double.parseDouble(text.trim()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String limit(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }
}
