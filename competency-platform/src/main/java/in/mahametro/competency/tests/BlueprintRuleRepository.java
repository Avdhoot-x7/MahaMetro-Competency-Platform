package in.mahametro.competency.tests;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlueprintRuleRepository extends JpaRepository<BlueprintRule, Long> {

    List<BlueprintRule> findByBlueprintId(Long blueprintId);

    void deleteByBlueprintId(Long blueprintId);
}
