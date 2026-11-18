package in.mahametro.competency.development;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterventionRepository extends JpaRepository<Intervention, Long> {

    List<Intervention> findByCompetencyCodeOrderByGapTypeAsc(String competencyCode);

    Optional<Intervention> findFirstByCompetencyCodeAndGapType(String competencyCode, String gapType);

    Optional<Intervention> findFirstByCompetencyCode(String competencyCode);

    List<Intervention> findByCompetencyCodeIsNullAndGapType(String gapType);
}
