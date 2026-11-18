package in.mahametro.competency.access;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRoleRepository extends JpaRepository<EmployeeRole, Long> {

    List<EmployeeRole> findByEmployeeId(Long employeeId);

    boolean existsByEmployeeIdAndRoleCodeAndScopeTypeAndScopeValue(Long employeeId, String roleCode, String scopeType, String scopeValue);
}
