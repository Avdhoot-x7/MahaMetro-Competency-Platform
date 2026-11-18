package in.mahametro.competency.proctoring;

import in.mahametro.competency.core.AuditService;
import in.mahametro.competency.delivery.Attempt;
import in.mahametro.competency.delivery.AttemptItem;
import in.mahametro.competency.delivery.AttemptItemRepository;
import in.mahametro.competency.delivery.AttemptRepository;
import in.mahametro.competency.delivery.DeliveryService;
import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.employees.EmployeeRepository;
import in.mahametro.competency.questions.Media;
import in.mahametro.competency.questions.MediaService;
import in.mahametro.competency.scoring.CompetencyScoreRepository;
import in.mahametro.competency.tests.AssessmentTest;
import in.mahametro.competency.tests.AssessmentTestRepository;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service managing proctoring event logs, candidate integrity checks, and proctor decisions.
 */
@Service
public class ProctoringService {

    public static final int DEFAULT_MAX_TAB_SWITCHES = 3;

    private final ProctorEventRepository eventRepository;
    private final AttemptRepository attemptRepository;
    private final AttemptItemRepository attemptItemRepository;
    private final AssessmentTestRepository testRepository;
    private final EmployeeRepository employeeRepository;
    private final CompetencyScoreRepository scoreRepository;
    private final TimingAnalysisService timingService;
    private final MediaService mediaService;
    private final DeliveryService deliveryService;
    private final AuditService audit;

    public ProctoringService(ProctorEventRepository eventRepository,
                             AttemptRepository attemptRepository,
                             AttemptItemRepository attemptItemRepository,
                             AssessmentTestRepository testRepository,
                             EmployeeRepository employeeRepository,
                             CompetencyScoreRepository scoreRepository,
                             TimingAnalysisService timingService,
                             MediaService mediaService,
                             DeliveryService deliveryService,
                             AuditService audit) {
        this.eventRepository = eventRepository;
        this.attemptRepository = attemptRepository;
        this.attemptItemRepository = attemptItemRepository;
        this.testRepository = testRepository;
        this.employeeRepository = employeeRepository;
        this.scoreRepository = scoreRepository;
        this.timingService = timingService;
        this.mediaService = mediaService;
        this.deliveryService = deliveryService;
        this.audit = audit;
    }

    /**
     * Records a client-side proctoring event during a test.
     */
    @Transactional
    public int recordEvent(Long attemptId, Employee candidate, String kind, String detail, Long mediaId) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Attempt not found: " + attemptId));

        if (!attempt.getEmployeeId().equals(candidate.getId())) {
            throw new IllegalStateException("Unauthorized attempt access.");
        }

        if (!attempt.isInProgress()) {
            return attempt.getTabSwitches();
        }

        ProctorEvent event = new ProctorEvent(attemptId, kind, detail, mediaId);
        eventRepository.save(event);

        if (ProctorEvent.KIND_TAB_HIDDEN.equalsIgnoreCase(kind) || ProctorEvent.KIND_BLUR.equalsIgnoreCase(kind)) {
            attempt.setTabSwitches(attempt.getTabSwitches() + 1);
            attemptRepository.save(attempt);
        }

        return attempt.getTabSwitches();
    }

    /**
     * Saves a webcam snapshot captured during proctoring.
     */
    @Transactional
    public Long saveSnapshot(Long attemptId, Employee candidate, MultipartFile file) throws IOException {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Attempt not found: " + attemptId));

        if (!attempt.getEmployeeId().equals(candidate.getId())) {
            throw new IllegalStateException("Unauthorized attempt access.");
        }

        Media media = mediaService.store(candidate, file);
        recordEvent(attemptId, candidate, ProctorEvent.KIND_SNAPSHOT, "Webcam periodic snapshot", media.getId());
        return media.getId();
    }

    /**
     * Evaluates integrity of an attempt (tab switch limit and rapid guessing).
     * Returns true if clean, or false if flagged.
     */
    public boolean checkIntegrity(Attempt attempt, List<AttemptItem> items, AssessmentTest test) {
        List<String> violations = new ArrayList<>();

        // 1. Tab switches check
        int maxTabs = DEFAULT_MAX_TAB_SWITCHES;
        if (test != null && test.isPractice()) {
            maxTabs = 999; // relaxed for practice
        }
        if (attempt.getTabSwitches() > maxTabs) {
            violations.add("Exceeded tab switch limit (" + attempt.getTabSwitches() + " / max " + maxTabs + ")");
        }

        // 2. Rapid guessing check
        TimingAnalysisService.TimingSummary timing = timingService.analyse(attempt, items);
        if (timing.isLowEffort()) {
            violations.add("Rapid guessing detected (Effort RTE " + timing.getRte() + "%, "
                    + timing.getRapidCount() + " rapid answers of " + timing.getAnsweredCount() + ")");
        }

        if (!violations.isEmpty()) {
            attempt.setStatus(Attempt.STATUS_FLAGGED);
            attempt.setFlagReason(String.join(" | ", violations));
            return false;
        }

        return true;
    }

    public List<ProctorEvent> getEventsForAttempt(Long attemptId) {
        return eventRepository.findByAttemptIdOrderByCreatedAtAsc(attemptId);
    }

    public List<Attempt> getFlaggedAttempts() {
        return attemptRepository.findByStatusOrderBySubmittedAtDesc(Attempt.STATUS_FLAGGED);
    }

    public List<Attempt> getAllRecentSubmissions() {
        return attemptRepository.findByStatusNotOrderBySubmittedAtDesc(Attempt.STATUS_IN_PROGRESS);
    }

    /**
     * Proctor accepts a flagged attempt: marks as SCORED and publishes scores.
     */
    @Transactional
    public void acceptAttempt(Long attemptId, Employee proctor, String note) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Attempt not found: " + attemptId));

        attempt.setStatus(Attempt.STATUS_SCORED);
        attempt.setReviewedBy(proctor.getEmpId());
        attempt.setReviewedAt(LocalDateTime.now());
        String reason = attempt.getFlagReason() == null ? "" : attempt.getFlagReason();
        attempt.setFlagReason((reason + " [ACCEPTED by " + proctor.getEmpId() + ": " + (note == null ? "" : note) + "]").trim());
        attemptRepository.save(attempt);

        AssessmentTest test = testRepository.findById(attempt.getTestId()).orElse(null);
        Employee candidate = employeeRepository.findById(attempt.getEmployeeId()).orElse(null);
        if (test != null && candidate != null) {
            deliveryService.publishAttemptScores(attempt, test, candidate);
        }

        audit.record(proctor.getEmpId(), "attempt_accepted", "attempt", attempt.getId().toString(), note);
    }

    /**
     * Proctor voids an attempt: marks as VOID and cleans up any score records.
     */
    @Transactional
    public void voidAttempt(Long attemptId, Employee proctor, String note) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Attempt not found: " + attemptId));

        attempt.setStatus(Attempt.STATUS_VOID);
        attempt.setReviewedBy(proctor.getEmpId());
        attempt.setReviewedAt(LocalDateTime.now());
        String reason = attempt.getFlagReason() == null ? "" : attempt.getFlagReason();
        attempt.setFlagReason((reason + " [VOIDED by " + proctor.getEmpId() + ": " + (note == null ? "" : note) + "]").trim());
        attemptRepository.save(attempt);

        // Delete any scores attributed to this attempt
        scoreRepository.deleteByRecordedBy("auto_test_" + attempt.getId());

        audit.record(proctor.getEmpId(), "attempt_voided", "attempt", attempt.getId().toString(), note);
    }
}
