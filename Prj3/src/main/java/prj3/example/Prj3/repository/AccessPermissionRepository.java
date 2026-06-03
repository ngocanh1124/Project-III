package prj3.example.Prj3.repository;

import prj3.example.Prj3.entity.AccessPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccessPermissionRepository extends JpaRepository<AccessPermission, Long> {
    
    List<AccessPermission> findByEmployeeId(Long employeeId);

    List<AccessPermission> findByDeviceId(Long deviceId);

    @Query("SELECT ap FROM AccessPermission ap WHERE ap.device.deviceCode = :deviceCode AND ap.isActive = true")
    List<AccessPermission> findByDeviceCode(@Param("deviceCode") String deviceCode);

    List<AccessPermission> findByEmployeeIdAndDeviceId(Long employeeId, Long deviceId);

    boolean existsByEmployeeIdAndDeviceId(Long employeeId, Long deviceId);
    
    
        @Query("SELECT ap FROM AccessPermission ap " +
           "WHERE ap.employee.cccd = :cccd " +
           "AND ap.device.deviceCode = :deviceCode " +
           "AND ap.isActive = true")
        Optional<AccessPermission> findByEmployeeCccdAndDeviceCode(
            @Param("cccd") String cccd,
            @Param("deviceCode") String deviceCode
        );

    
    @Query("SELECT ap FROM AccessPermission ap " +
           "WHERE ap.employee.cccd LIKE CONCAT('%', :suffix) " +
           "AND ap.device.deviceCode = :deviceCode " +
           "AND ap.isActive = true")
    java.util.List<AccessPermission> findByEmployeeCccdSuffixAndDeviceCode(
        @Param("suffix") String suffix,
        @Param("deviceCode") String deviceCode
    );
}