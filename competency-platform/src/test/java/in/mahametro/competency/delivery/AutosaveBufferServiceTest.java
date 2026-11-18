package in.mahametro.competency.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AutosaveBufferServiceTest {

    @Mock
    private AttemptItemRepository attemptItemRepository;

    private AttemptItem mockItem;

    @BeforeEach
    void setUp() {
        mockItem = new AttemptItem(1L, 10L, 1, "ENG", "T1", "single", java.math.BigDecimal.ONE, "1,2,3");
        mockItem.setTimeSpentSeconds(5);
        mockItem.setSelectedOptions("");
        mockItem.setAnsweredText("");
    }

    @Test
    void testDirectDatabaseAutosaveBufferPersistsImmediately() {
        when(attemptItemRepository.findByAttemptIdAndPosition(1L, 1))
                .thenReturn(Optional.of(mockItem));
        when(attemptItemRepository.save(any(AttemptItem.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DirectDatabaseAutosaveBuffer directBuffer = new DirectDatabaseAutosaveBuffer(attemptItemRepository);
        directBuffer.bufferAnswer(1L, 1, "opt1,opt2", "my answer", 10);

        verify(attemptItemRepository, times(1)).save(mockItem);
        assertThat(mockItem.getSelectedOptions()).isEqualTo("opt1,opt2");
        assertThat(mockItem.getAnsweredText()).isEqualTo("my answer");
        assertThat(mockItem.getTimeSpentSeconds()).isEqualTo(15); // 5 + 10
    }

    @Test
    void testRedisAutosaveBufferBatchesAndFlushesOnDemand() {
        when(attemptItemRepository.findByAttemptIdAndPosition(1L, 1))
                .thenReturn(Optional.of(mockItem));
        when(attemptItemRepository.save(any(AttemptItem.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RedisAutosaveBuffer redisBuffer = new RedisAutosaveBuffer(attemptItemRepository);

        // Multiple rapid autosaves for the same question
        redisBuffer.bufferAnswer(1L, 1, "A", "draft", 3);
        redisBuffer.bufferAnswer(1L, 1, "B", "final text", 7);

        // Before flush, repository save should NOT have been called
        verify(attemptItemRepository, never()).save(any());

        // Flushing the specific attempt
        redisBuffer.flushAttempt(1L);

        // Now save is called once with aggregated results
        verify(attemptItemRepository, times(1)).save(mockItem);
        assertThat(mockItem.getSelectedOptions()).isEqualTo("B");
        assertThat(mockItem.getAnsweredText()).isEqualTo("final text");
        assertThat(mockItem.getTimeSpentSeconds()).isEqualTo(15); // 5 + (3 + 7)
    }

    @Test
    void testRedisAutosaveBufferFlushAll() {
        when(attemptItemRepository.findByAttemptIdAndPosition(1L, 1))
                .thenReturn(Optional.of(mockItem));

        RedisAutosaveBuffer redisBuffer = new RedisAutosaveBuffer(attemptItemRepository);
        redisBuffer.bufferAnswer(1L, 1, "C", "", 4);

        redisBuffer.flushAll();

        verify(attemptItemRepository, times(1)).save(mockItem);
        assertThat(mockItem.getSelectedOptions()).isEqualTo("C");
        assertThat(mockItem.getTimeSpentSeconds()).isEqualTo(9);
    }
}
