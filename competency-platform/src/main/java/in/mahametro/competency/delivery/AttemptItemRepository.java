package in.mahametro.competency.delivery;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptItemRepository extends JpaRepository<AttemptItem, Long> {

    List<AttemptItem> findByAttemptIdOrderByPositionAsc(Long attemptId);

    Optional<AttemptItem> findByAttemptIdAndPosition(Long attemptId, int position);
}
