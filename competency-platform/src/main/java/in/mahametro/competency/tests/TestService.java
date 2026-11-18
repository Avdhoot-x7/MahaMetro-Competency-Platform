package in.mahametro.competency.tests;

import in.mahametro.competency.core.AuditService;
import in.mahametro.competency.employees.Employee;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Business logic for tests, scheduling, eligibility, and state transitions. */
@Service
public class TestService {

    private final AssessmentTestRepository testRepository;
    private final AuditService audit;

    public TestService(AssessmentTestRepository testRepository, AuditService audit) {
        this.testRepository = testRepository;
        this.audit = audit;
    }

    @Transactional
    public AssessmentTest createTest(AssessmentTest test, Employee creator) {
        if (test.getCode() == null || test.getCode().isBlank()) {
            throw new IllegalArgumentException("Test code is required.");
        }
        if (testRepository.existsByCode(test.getCode().trim().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Test code " + test.getCode() + " already exists.");
        }
        test.setCode(test.getCode().trim().toUpperCase(Locale.ROOT));
        test.setTitleEn(test.getTitleEn().trim());
        test.setStatus(AssessmentTest.STATUS_DRAFT);
        test.setCreatedBy(creator.getEmpId());
        AssessmentTest saved = testRepository.save(test);
        audit.record(creator.getEmpId(), "test_created", "test", saved.getId().toString(), saved.getCode());
        return saved;
    }

    @Transactional
    public AssessmentTest publishTest(Long testId, Employee actor) {
        AssessmentTest test = testRepository.findById(testId)
                .orElseThrow(() -> new IllegalArgumentException("Test not found: " + testId));
        test.setStatus(AssessmentTest.STATUS_PUBLISHED);
        AssessmentTest saved = testRepository.save(test);
        audit.record(actor.getEmpId(), "test_published", "test", saved.getId().toString(), saved.getCode());
        return saved;
    }

    @Transactional
    public AssessmentTest closeTest(Long testId, Employee actor) {
        AssessmentTest test = testRepository.findById(testId)
                .orElseThrow(() -> new IllegalArgumentException("Test not found: " + testId));
        test.setStatus(AssessmentTest.STATUS_CLOSED);
        AssessmentTest saved = testRepository.save(test);
        audit.record(actor.getEmpId(), "test_closed", "test", saved.getId().toString(), saved.getCode());
        return saved;
    }

    /** Determines whether a candidate is eligible to take a test right now. */
    public boolean isEligible(AssessmentTest test, Employee employee) {
        if (test == null || employee == null || !employee.isActive()) {
            return false;
        }
        if (!AssessmentTest.STATUS_PUBLISHED.equalsIgnoreCase(test.getStatus())) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        if (test.getStartAt() != null && now.isBefore(test.getStartAt())) {
            return false;
        }
        if (test.getEndAt() != null && now.isAfter(test.getEndAt())) {
            return false;
        }

        String targetType = test.getTargetType() == null ? AssessmentTest.TARGET_ALL : test.getTargetType();
        String targetValue = test.getTargetValue() == null ? "" : test.getTargetValue().trim();

        if (AssessmentTest.TARGET_ALL.equalsIgnoreCase(targetType)) {
            return true;
        }

        List<String> values = Arrays.stream(targetValue.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .toList();

        if (values.isEmpty()) {
            return true;
        }

        return switch (targetType.toUpperCase(Locale.ROOT)) {
            case AssessmentTest.TARGET_ROLE_FAMILY ->
                    employee.getRoleFamilyCode() != null && values.contains(employee.getRoleFamilyCode().toUpperCase(Locale.ROOT));
            case AssessmentTest.TARGET_GRADE ->
                    employee.getGradeCode() != null && values.contains(employee.getGradeCode().toUpperCase(Locale.ROOT));
            case AssessmentTest.TARGET_DEPARTMENT ->
                    employee.getDepartment() != null && values.contains(employee.getDepartment().toUpperCase(Locale.ROOT));
            case AssessmentTest.TARGET_EMPLOYEE ->
                    employee.getEmpId() != null && values.contains(employee.getEmpId().toUpperCase(Locale.ROOT));
            default -> false;
        };
    }

    public List<AssessmentTest> findAvailableTests(Employee employee) {
        List<AssessmentTest> published = testRepository.findByStatusOrderByCreatedAtDesc(AssessmentTest.STATUS_PUBLISHED);
        List<AssessmentTest> eligible = new ArrayList<>();
        for (AssessmentTest t : published) {
            if (isEligible(t, employee)) {
                eligible.add(t);
            }
        }
        return eligible;
    }
}
