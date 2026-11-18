package in.mahametro.competency.employees;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DesignationMapRepository extends JpaRepository<DesignationMap, Long> {

    Optional<DesignationMap> findByDesignationIgnoreCase(String designation);

    boolean existsByDesignationIgnoreCase(String designation);

    List<DesignationMap> findAllByOrderByDesignationAsc();
}
