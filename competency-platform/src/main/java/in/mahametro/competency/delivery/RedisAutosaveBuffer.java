package in.mahametro.competency.delivery;

import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * High-concurrency autosave buffer. Debounces and buffers candidate answer changes
 * in memory / Redis before flushing them in batches to MySQL, eliminating write contention
 * across thousands of concurrent candidates.
 */
@Service
@ConditionalOnProperty(name = "app.redis.autosave-buffer.enabled", havingValue = "true")
public class RedisAutosaveBuffer implements AutosaveBufferService {

    private static final Logger log = LoggerFactory.getLogger(RedisAutosaveBuffer.class);

    private final AttemptItemRepository attemptItemRepository;
    private final Map<String, BufferedAnswer> buffer = new ConcurrentHashMap<>();

    public RedisAutosaveBuffer(AttemptItemRepository attemptItemRepository) {
        this.attemptItemRepository = attemptItemRepository;
    }

    public static class BufferedAnswer {
        private final Long attemptId;
        private final int position;
        private volatile String selectedOptions;
        private volatile String answeredText;
        private volatile int accumulatedTimeSeconds;
        private volatile LocalDateTime lastUpdated;

        public BufferedAnswer(Long attemptId, int position, String selectedOptions, String answeredText,
                int timeSpentSeconds) {
            this.attemptId = attemptId;
            this.position = position;
            this.selectedOptions = selectedOptions;
            this.answeredText = answeredText;
            this.accumulatedTimeSeconds = Math.max(0, timeSpentSeconds);
            this.lastUpdated = LocalDateTime.now();
        }

        public synchronized void merge(String selectedOptions, String answeredText, int timeSpentSeconds) {
            this.selectedOptions = selectedOptions;
            this.answeredText = answeredText;
            this.accumulatedTimeSeconds += Math.max(0, timeSpentSeconds);
            this.lastUpdated = LocalDateTime.now();
        }

        public Long getAttemptId() {
            return attemptId;
        }

        public int getPosition() {
            return position;
        }

        public String getSelectedOptions() {
            return selectedOptions;
        }

        public String getAnsweredText() {
            return answeredText;
        }

        public int getAccumulatedTimeSeconds() {
            return accumulatedTimeSeconds;
        }

        public LocalDateTime getLastUpdated() {
            return lastUpdated;
        }
    }

    private String key(Long attemptId, int position) {
        return attemptId + ":" + position;
    }

    @Override
    public void bufferAnswer(Long attemptId, int position, String selectedOptions, String answeredText,
            int timeSpentSeconds) {
        String k = key(attemptId, position);
        buffer.compute(k, (key, existing) -> {
            if (existing == null) {
                return new BufferedAnswer(attemptId, position, selectedOptions, answeredText, timeSpentSeconds);
            }
            existing.merge(selectedOptions, answeredText, timeSpentSeconds);
            return existing;
        });
    }

    @Override
    @Transactional
    public void flushAttempt(Long attemptId) {
        List<String> keysToFlush = new ArrayList<>();
        for (Map.Entry<String, BufferedAnswer> entry : buffer.entrySet()) {
            if (entry.getValue().getAttemptId().equals(attemptId)) {
                keysToFlush.add(entry.getKey());
            }
        }

        for (String k : keysToFlush) {
            BufferedAnswer ba = buffer.remove(k);
            if (ba != null) {
                persistBufferedAnswer(ba);
            }
        }
    }

    @Override
    @Transactional
    public void flushAll() {
        if (buffer.isEmpty()) {
            return;
        }
        log.info("Flushing {} buffered autosaves to persistent storage", buffer.size());
        List<String> keys = new ArrayList<>(buffer.keySet());
        for (String k : keys) {
            BufferedAnswer ba = buffer.remove(k);
            if (ba != null) {
                persistBufferedAnswer(ba);
            }
        }
    }

    /**
     * Periodic batch flush scheduled every 5 seconds to persist buffered updates.
     */
    @Scheduled(fixedDelay = 5000)
    public void periodicFlush() {
        flushAll();
    }

    @PreDestroy
    public void shutdown() {
        log.info("Autosave buffer shutting down, performing final flush");
        flushAll();
    }

    private void persistBufferedAnswer(BufferedAnswer ba) {
        attemptItemRepository.findByAttemptIdAndPosition(ba.getAttemptId(), ba.getPosition()).ifPresent(item -> {
            item.setSelectedOptions(ba.getSelectedOptions() == null ? "" : ba.getSelectedOptions().trim());
            item.setAnsweredText(ba.getAnsweredText() == null ? "" : ba.getAnsweredText().trim());
            item.setTimeSpentSeconds(item.getTimeSpentSeconds() + ba.getAccumulatedTimeSeconds());
            item.setAnsweredAt(ba.getLastUpdated());
            attemptItemRepository.save(item);
        });
    }
}
