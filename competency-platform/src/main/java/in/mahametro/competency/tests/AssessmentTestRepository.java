package in.mahametro.competency.tests;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentTestRepository extends JpaRepository<AssessmentTest, Long> {

    Optional<AssessmentTest> findByCode(String code);

    boolean existsByCode(String code);

    List<AssessmentTest> findByStatusOrderByCreatedAtDesc(String status);

    Page<AssessmentTest> findByStatus(String status, Pageable pageable);

    long countByStatus(String status);
}
