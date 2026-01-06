package prj3.example.Prj3.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import prj3.example.Prj3.model.Employee;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByCccdAndOrgId(String cccd, Long orgId);
    Optional<Employee> findByCccd(String cccd);
    List<Employee> findByOrgId(Long orgId);
}