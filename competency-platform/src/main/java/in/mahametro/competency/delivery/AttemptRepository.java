package in.mahametro.competency.delivery;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    List<Attempt> findByTestIdAndEmployeeIdOrderByStartedAtDesc(Long testId, Long employeeId);

    long countByTestIdAndEmployeeIdAndStatusNot(Long testId, Long employeeId, String status);

    Optional<Attempt> findFirstByTestIdAndEmployeeIdAndStatus(Long testId, Long employeeId, String status);

    List<Attempt> findByEmployeeIdOrderByStartedAtDesc(Long employeeId);

    List<Attempt> findByStatusOrderBySubmittedAtDesc(String status);

    List<Attempt> findByStatusNotOrderBySubmittedAtDesc(String status);
}
