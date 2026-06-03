package prj3.example.Prj3.repository;

import prj3.example.Prj3.entity.Employee;
import prj3.example.Prj3.enums.ActivationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByCccd(String cccd);

    
    @org.springframework.data.jpa.repository.Query("SELECT e FROM Employee e WHERE e.cccd LIKE CONCAT('%', :suffix)")
    java.util.List<Employee> findByCccdEndingWith(@org.springframework.data.repository.query.Param("suffix") String suffix);
    
    Optional<Employee> findByCccdAndOrganizationId(String cccd, Long organizationId);
    
    Page<Employee> findByCccdOrFullNameContaining(String cccd, String fullName, Pageable pageable);
    
    Page<Employee> findByIsActive(boolean isActive, Pageable pageable);
    
    List<Employee> findByIsActive(boolean isActive);
    
    long countByIsActive(boolean isActive);

    
    List<Employee> findByActivationStatus(ActivationStatus activationStatus);

    long countByActivationStatus(ActivationStatus activationStatus);
}