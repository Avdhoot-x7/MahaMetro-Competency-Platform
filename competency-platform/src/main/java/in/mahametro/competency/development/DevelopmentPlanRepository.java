package in.mahametro.competency.development;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DevelopmentPlanRepository extends JpaRepository<DevelopmentPlan, Long> {

    Optional<DevelopmentPlan> findFirstByEmployeeIdOrderByCreatedAtDescIdDesc(Long employeeId);

    List<DevelopmentPlan> findByEmployeeIdAndCycleAndStatus(Long employeeId, String cycle, String status);

    List<DevelopmentPlan> findByStatusOrderByCreatedAtDesc(String status);

    List<DevelopmentPlan> findByEmployeeIdInOrderByCreatedAtDesc(List<Long> employeeIds);
}
