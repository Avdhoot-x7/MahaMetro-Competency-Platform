package in.mahametro.competency.questions;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionHistoryRepository extends JpaRepository<QuestionHistory, Long> {

    List<QuestionHistory> findByQuestionIdOrderByIdAsc(Long questionId);
}
