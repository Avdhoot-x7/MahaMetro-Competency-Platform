package in.mahametro.competency.employees;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmpId(String empId);

    Optional<Employee> findFirstByEmailIgnoreCase(String email);

    Page<Employee> findByRoleFamilyCodeIsNotNull(Pageable pageable);

    Page<Employee> findByEmpIdContainingIgnoreCaseOrNameEnContainingIgnoreCase(String empId, String nameEn, Pageable pageable);

    java.util.List<Employee> findByDesignationIgnoreCase(String designation);

    java.util.List<Employee> findByReportingOfficerAndActiveTrue(String reportingOfficer);

    java.util.List<Employee> findByDepartmentAndActiveTrue(String department);

    java.util.List<Employee> findByActiveTrue();

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT e.department FROM Employee e WHERE e.department IS NOT NULL AND e.department != '' ORDER BY e.department ASC")
    java.util.List<String> findDistinctDepartments();

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT e.reportingOfficer FROM Employee e WHERE e.reportingOfficer IS NOT NULL AND e.reportingOfficer != ''")
    java.util.List<String> findDistinctReportingOfficers();
}



