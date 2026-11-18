package in.mahametro.competency.framework;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CutScoreRepository extends JpaRepository<CutScore, Integer> {

    List<CutScore> findAllByOrderByLevelNoAsc();
}
