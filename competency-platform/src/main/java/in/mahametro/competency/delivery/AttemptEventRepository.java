package in.mahametro.competency.delivery;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AttemptEventRepository extends JpaRepository<AttemptEvent, Long> {
    List<AttemptEvent> findByAttemptIdOrderByAtAsc(Long attemptId);
    List<AttemptEvent> findByAttemptIdAndKindOrderByAtAsc(Long attemptId, String kind);
    long countByAttemptIdAndKind(Long attemptId, String kind);
}
