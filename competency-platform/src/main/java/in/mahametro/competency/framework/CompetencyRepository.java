package in.mahametro.competency.framework;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetencyRepository extends JpaRepository<Competency, String> {
    List<Competency> findAllByOrderByCodeAsc();
}
