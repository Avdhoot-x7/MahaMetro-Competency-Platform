package in.mahametro.competency.proctoring;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProctorEventRepository extends JpaRepository<ProctorEvent, Long> {

    List<ProctorEvent> findByAttemptIdOrderByCreatedAtAsc(Long attemptId);

    long countByAttemptIdAndKind(Long attemptId, String kind);
}
