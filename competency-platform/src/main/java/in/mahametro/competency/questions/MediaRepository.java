package in.mahametro.competency.questions;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaRepository extends JpaRepository<Media, Long> {

    Optional<Media> findByStoredName(String storedName);

    List<Media> findByOwnerEmpIdOrderByCreatedAtDesc(String ownerEmpId);
}
