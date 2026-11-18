package in.mahametro.competency.framework;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleProfileRepository extends JpaRepository<RoleProfile, Long> {

    List<RoleProfile> findByRoleFamilyCode(String roleFamilyCode);
}
