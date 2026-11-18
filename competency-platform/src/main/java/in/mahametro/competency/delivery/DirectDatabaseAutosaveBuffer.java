package in.mahametro.competency.delivery;

import java.time.LocalDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default direct database autosave implementation. Used in standalone and development
 * environments, or when Redis autosave buffering is disabled.
 */
@Service
@ConditionalOnProperty(name = "app.redis.autosave-buffer.enabled", havingValue = "false", matchIfMissing = true)
public class DirectDatabaseAutosaveBuffer implements AutosaveBufferService {

    private final AttemptItemRepository attemptItemRepository;

    public DirectDatabaseAutosaveBuffer(AttemptItemRepository attemptItemRepository) {
        this.attemptItemRepository = attemptItemRepository;
    }

    @Override
    @Transactional
    public void bufferAnswer(Long attemptId, int position, String selectedOptions, String answeredText,
            int timeSpentSeconds) {
        attemptItemRepository.findByAttemptIdAndPosition(attemptId, position).ifPresent(item -> {
            item.setSelectedOptions(selectedOptions == null ? "" : selectedOptions.trim());
            item.setAnsweredText(answeredText == null ? "" : answeredText.trim());
            item.setTimeSpentSeconds(item.getTimeSpentSeconds() + Math.max(0, timeSpentSeconds));
            item.setAnsweredAt(LocalDateTime.now());
            attemptItemRepository.save(item);
        });
    }

    @Override
    public void flushAttempt(Long attemptId) {
        // Direct database mode is synchronous, no buffered answers to flush
    }

    @Override
    public void flushAll() {
        // Direct database mode is synchronous, no buffered answers to flush
    }
}
