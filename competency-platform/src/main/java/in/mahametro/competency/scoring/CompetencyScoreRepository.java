package in.mahametro.competency.scoring;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetencyScoreRepository extends JpaRepository<CompetencyScore, Long> {

    List<CompetencyScore> findByEmployeeIdInOrderByIdAsc(Collection<Long> employeeIds);

    List<CompetencyScore> findByEmployeeId(Long employeeId);

    void deleteByRecordedBy(String recordedBy);
}
