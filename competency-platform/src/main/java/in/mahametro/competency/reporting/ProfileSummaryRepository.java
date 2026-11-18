package in.mahametro.competency.reporting;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfileSummaryRepository extends JpaRepository<ProfileSummary, Long> {

    List<ProfileSummary> findByEmployeeIdIn(Collection<Long> employeeIds);

    long countByReadiness(String readiness);

    @Query("SELECT ps.readiness, COUNT(ps) FROM ProfileSummary ps WHERE ps.employeeId IN :empIds GROUP BY ps.readiness")
    List<Object[]> countByReadinessForEmployees(@Param("empIds") Collection<Long> empIds);

    @Query("SELECT ps.readiness, COUNT(ps) FROM ProfileSummary ps GROUP BY ps.readiness")
    List<Object[]> countAllByReadiness();

    @Query("SELECT ps FROM ProfileSummary ps JOIN in.mahametro.competency.employees.Employee e ON ps.employeeId = e.id WHERE e.department = :dept")
    List<ProfileSummary> findByDepartment(@Param("dept") String dept);
}
