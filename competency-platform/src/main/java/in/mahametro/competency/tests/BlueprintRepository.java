package in.mahametro.competency.tests;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlueprintRepository extends JpaRepository<Blueprint, Long> {

    List<Blueprint> findByRoleFamilyCode(String roleFamilyCode);
}
