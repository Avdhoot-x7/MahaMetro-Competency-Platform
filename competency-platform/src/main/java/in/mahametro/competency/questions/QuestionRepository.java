package in.mahametro.competency.questions;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    Page<Question> findByStatus(String status, Pageable pageable);

    List<Question> findByStatus(String status);

    List<Question> findByCompetencyCodeAndStatus(String competencyCode, String status);

    long countByStatus(String status);

    Optional<Question> findFirstByCompetencyCodeAndTopicNoOrderByIdAsc(String competencyCode, String topicNo);

    boolean existsByCompetencyCodeAndTopicNoAndVariationNoAndStatusNot(String competencyCode, String topicNo,
            int variationNo, String status);
}
