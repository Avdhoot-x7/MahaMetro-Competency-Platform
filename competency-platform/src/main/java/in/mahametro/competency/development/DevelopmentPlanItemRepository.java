package in.mahametro.competency.development;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DevelopmentPlanItemRepository extends JpaRepository<DevelopmentPlanItem, Long> {

    List<DevelopmentPlanItem> findByPlanIdOrderByPriorityAscGapDescCompetencyCodeAsc(Long planId);

    void deleteByPlanId(Long planId);
}
