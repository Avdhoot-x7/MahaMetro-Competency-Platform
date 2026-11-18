package in.mahametro.competency.delivery;

/**
 * Service contract for buffering and flushing candidate autosaves during test delivery.
 * High-concurrency assessments can generate thousands of autosaves per minute; buffering
 * prevents database lock contention and connection pool exhaustion.
 */
public interface AutosaveBufferService {

    /**
     * Records or buffers a candidate's answer for a question in an attempt.
     */
    void bufferAnswer(Long attemptId, int position, String selectedOptions, String answeredText, int timeSpentSeconds);

    /**
     * Flushes any buffered answers for the given attempt to the persistent database.
     * Guaranteed to be executed prior to attempt grading and scoring.
     */
    void flushAttempt(Long attemptId);

    /**
     * Flushes all currently pending buffered answers across all attempts to the database.
     */
    void flushAll();
}
