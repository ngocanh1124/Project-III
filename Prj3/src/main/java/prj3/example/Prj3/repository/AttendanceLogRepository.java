package prj3.example.Prj3.repository;

import prj3.example.Prj3.entity.AttendanceLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long> {
    
    List<AttendanceLog> findAllByOrderByScanTimeDesc();
    
    
    List<AttendanceLog> findByCccd(String cccd);
    
    
    @Query("SELECT a FROM AttendanceLog a WHERE " +
           "(:cccd IS NULL OR a.cccd = :cccd) AND " +
           "(:deviceCode IS NULL OR a.deviceCode = :deviceCode) AND " +
           "(:startTime IS NULL OR a.scanTime >= :startTime) AND " +
           "(:endTime IS NULL OR a.scanTime <= :endTime) AND " +
           "(:scoreMin IS NULL OR a.matchScore >= :scoreMin) AND " +
           "(:scoreMax IS NULL OR a.matchScore <= :scoreMax) AND " +
           "(:accessGranted IS NULL OR a.accessGranted = :accessGranted) AND " +
           "(a.comparisonMode IS NULL OR a.comparisonMode <> 'REMOTE_UNLOCK')")
    Page<AttendanceLog> findByFilters(
            @Param("cccd") String cccd,
            @Param("deviceCode") String deviceCode,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("scoreMin") Double scoreMin,
            @Param("scoreMax") Double scoreMax,
            @Param("accessGranted") Boolean accessGranted,
            Pageable pageable
    );
    
    
    @Query("SELECT COUNT(a) FROM AttendanceLog a WHERE a.scanTime >= :startTime AND a.scanTime <= :endTime AND (a.comparisonMode IS NULL OR a.comparisonMode <> 'REMOTE_UNLOCK')")
    Long countByDateRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
    
    
    @Query("SELECT COUNT(a) FROM AttendanceLog a WHERE " +
           "a.deviceCode = :deviceCode AND " +
           "a.scanTime >= :startTime AND a.scanTime <= :endTime AND " +
           "(a.comparisonMode IS NULL OR a.comparisonMode <> 'REMOTE_UNLOCK')")
    Long countByDeviceAndDateRange(
            @Param("deviceCode") String deviceCode,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
    
    
    @Query("SELECT COUNT(a) FROM AttendanceLog a WHERE " +
           "a.deviceCode = :deviceCode AND " +
           "a.scanTime >= :startTime AND a.scanTime <= :endTime AND " +
           "a.matched = :matched AND " +
           "(a.comparisonMode IS NULL OR a.comparisonMode <> 'REMOTE_UNLOCK')")
    Long countByDeviceAndDateRangeAndStatus(
            @Param("deviceCode") String deviceCode,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("matched") boolean matched
    );
    
    
    @Query("SELECT COUNT(DISTINCT CAST(a.scanTime AS date)) FROM AttendanceLog a WHERE " +
           "a.cccd = :cccd AND " +
           "a.scanTime >= :startTime AND a.scanTime <= :endTime")
    Long countDistinctDatesByCccdAndDateRange(
            @Param("cccd") String cccd,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
    
    
    @Query("SELECT COUNT(a) FROM AttendanceLog a WHERE " +
           "a.scanTime >= :startTime AND a.scanTime <= :endTime AND " +
           "a.matched = :matched AND " +
           "(a.comparisonMode IS NULL OR a.comparisonMode <> 'REMOTE_UNLOCK')")
    Long countByDateRangeAndStatus(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("matched") boolean matched
    );
    
    
    @Query("SELECT a.scanTime FROM AttendanceLog a WHERE a.cccd = :cccd ORDER BY a.scanTime DESC LIMIT 1")
    LocalDateTime findLastScanByCccd(@Param("cccd") String cccd);

    
    @Query("SELECT a FROM AttendanceLog a WHERE " +
           "(:deviceCode IS NULL OR a.deviceCode = :deviceCode) AND " +
           "(:startTime IS NULL OR a.scanTime >= :startTime) AND " +
           "(:endTime IS NULL OR a.scanTime <= :endTime) AND " +
           "a.matchScore >= :minScore AND a.matchScore <= :maxScore " +
           "ORDER BY a.scanTime DESC")
    Page<AttendanceLog> findSpecialCases(
            @Param("deviceCode") String deviceCode,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("minScore") double minScore,
            @Param("maxScore") double maxScore,
            Pageable pageable
    );

    
    @Query("SELECT COUNT(a) FROM AttendanceLog a WHERE a.matched = :matched AND (a.comparisonMode IS NULL OR a.comparisonMode <> 'REMOTE_UNLOCK')")
    Long countByStatus(@Param("matched") boolean matched);

    
    @Query("SELECT COUNT(a) FROM AttendanceLog a WHERE (a.comparisonMode IS NULL OR a.comparisonMode <> 'REMOTE_UNLOCK')")
    Long countExcludingRemoteUnlocks();

    
    @Query("SELECT COUNT(a) FROM AttendanceLog a WHERE " +
            "a.scanTime >= :startTime AND a.scanTime <= :endTime AND " +
            "(a.comparisonMode IS NULL OR a.comparisonMode <> 'REMOTE_UNLOCK') AND " +
            "(a.alertType IS NULL OR a.alertType <> 'OUTSIDE_HOURS') AND " +
            "a.matched = :matched")
    Long countByDateRangeAndStatusForAuthRate(
             @Param("startTime") LocalDateTime startTime,
             @Param("endTime") LocalDateTime endTime,
             @Param("matched") boolean matched
    );

    
    @Query("SELECT COUNT(a) FROM AttendanceLog a WHERE " +
            "(a.comparisonMode IS NULL OR a.comparisonMode <> 'REMOTE_UNLOCK') AND " +
            "(a.alertType IS NULL OR a.alertType <> 'OUTSIDE_HOURS') AND " +
            "a.matched = :matched")
    Long countByStatusForAuthRate(@Param("matched") boolean matched);

    
    @Query("SELECT COUNT(a) FROM AttendanceLog a WHERE " +
           "a.deviceCode = :deviceCode AND " +
           "a.scanTime >= :startTime AND a.scanTime <= :endTime AND " +
           "(a.comparisonMode IS NULL OR a.comparisonMode <> 'REMOTE_UNLOCK') AND " +
           "(a.alertType IS NULL OR a.alertType <> 'OUTSIDE_HOURS') AND " +
           "a.matched = :matched")
    Long countByDeviceAndDateRangeAndStatusForAuthRate(
            @Param("deviceCode") String deviceCode,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("matched") boolean matched
    );

    
    @Query(value = "SELECT a FROM AttendanceLog a WHERE " +
           "(:deviceCode IS NULL OR a.deviceCode = :deviceCode) AND " +
           "a.comparisonMode = 'REMOTE_UNLOCK'",
           countQuery = "SELECT COUNT(a) FROM AttendanceLog a WHERE " +
           "(:deviceCode IS NULL OR a.deviceCode = :deviceCode) AND " +
           "a.comparisonMode = 'REMOTE_UNLOCK'")
    Page<AttendanceLog> findRemoteUnlocks(
            @Param("deviceCode") String deviceCode,
            Pageable pageable
    );
    
    @Query(value = "SELECT a FROM AttendanceLog a WHERE a.alertType = 'OUTSIDE_HOURS' AND (:deviceCode IS NULL OR a.deviceCode = :deviceCode) ORDER BY a.scanTime DESC",
           countQuery = "SELECT COUNT(a) FROM AttendanceLog a WHERE a.alertType = 'OUTSIDE_HOURS' AND (:deviceCode IS NULL OR a.deviceCode = :deviceCode)")
    Page<AttendanceLog> findViolations(@Param("deviceCode") String deviceCode, Pageable pageable);
}