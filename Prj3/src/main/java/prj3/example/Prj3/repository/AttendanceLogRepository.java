package prj3.example.Prj3.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import prj3.example.Prj3.model.AttendanceLog;

@Repository
public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long> {
    List<AttendanceLog> findAllByOrderByTimestampDesc();
    List<AttendanceLog> findByOrgId(Long orgId);
    List<AttendanceLog> findByEmployeeId(Long employeeId);
}