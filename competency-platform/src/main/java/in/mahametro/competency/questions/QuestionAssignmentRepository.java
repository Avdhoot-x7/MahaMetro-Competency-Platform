package in.mahametro.competency.questions;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionAssignmentRepository extends JpaRepository<QuestionAssignment, Long> {

    List<QuestionAssignment> findByContributorEmpIdAndStatus(String contributorEmpId, String status);
}
