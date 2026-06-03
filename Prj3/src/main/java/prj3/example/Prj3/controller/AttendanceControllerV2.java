package prj3.example.Prj3.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import prj3.example.Prj3.dto.*;
import prj3.example.Prj3.entity.AttendanceLog;
import prj3.example.Prj3.entity.Employee;
import prj3.example.Prj3.entity.Device;
import prj3.example.Prj3.service.AttendanceService;
import prj3.example.Prj3.repository.AttendanceLogRepository;
import prj3.example.Prj3.repository.EmployeeRepository;
import prj3.example.Prj3.repository.DeviceRepository;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.io.IOException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@RestController
@RequestMapping("/api/v2/attendance")
public class AttendanceControllerV2 {
    
    private static final Logger logger = LoggerFactory.getLogger(AttendanceControllerV2.class);

    @Autowired 
    private AttendanceService attendanceService;

    @Autowired 
    private AttendanceLogRepository attendanceLogRepository;
    
    @Autowired
    private EmployeeRepository employeeRepository;

    

    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of("status", "online", "timestamp", LocalDateTime.now().toString()));
    }
    
    @Autowired
    private DeviceRepository deviceRepository;

    

    @PostMapping("/record")
    public ResponseEntity<APIResponseDTO<AttendanceRecordResponseDTO>> recordAttendance(
            @Valid @RequestBody AttendanceRecordRequestDTO request,
            BindingResult validationResult) {
        
        logger.info("Recording attendance - CCCD: {}, Device: {}, Score: {}", 
                   request.getCccd(), request.getDeviceCode(), request.getScore());
        
        
        if (validationResult.hasErrors()) {
            String errors = validationResult.getAllErrors().stream()
                    .map(e -> e.getDefaultMessage())
                    .collect(Collectors.joining(", "));
            logger.warn("Validation failed: {}", errors);
            return ResponseEntity.badRequest()
                    .body(APIResponseDTO.error("VALIDATION_ERROR", "Invalid request", errors));
        }

        try {
            
            AttendanceRecordResponseDTO response = attendanceService.processAttendanceWithLayer2Validation(request);
            
            if (response.getAccessGranted()) {
                logger.info("Attendance recorded successfully - CCCD: {}", request.getCccd());
                return ResponseEntity.ok(
                        APIResponseDTO.ok(response, "Attendance recorded successfully")
                );
            } else if ("PENDING_APPROVAL".equals(response.getStatus())) {
                logger.info("Pending approval created - CCCD: {}, logId: {}", request.getCccd(), response.getAttendanceId());
                return ResponseEntity.accepted()   
                        .body(APIResponseDTO.ok(response, "Khuôn mặt đã xác minh. Đang chờ phê duyệt từ nhân viên quản lý."));
            } else {
                logger.warn("Access denied - CCCD: {} - Reason: {}", 
                           request.getCccd(), response.getAuthorizationReason());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(APIResponseDTO.error("ACCESS_DENIED", 
                               response.getAuthorizationReason(), 
                               "User not authorized for this device"));
            }
        } catch (Exception e) {
            logger.error("Error recording attendance - CCCD: {}, cause: {}", request.getCccd(), e.toString(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponseDTO.error("INTERNAL_ERROR", 
                           "Failed to record attendance", e.getMessage()));
        }
    }

    

    @PostMapping("/{logId}/approve")
    public ResponseEntity<APIResponseDTO<Map<String, Object>>> approveAccess(@PathVariable Long logId) {
        logger.info("Approving pending access request id={}", logId);
        try {
            Map<String, Object> result = attendanceService.approveAccess(logId);
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(APIResponseDTO.ok(result, (String) result.get("message")));
            } else {
                return ResponseEntity.badRequest()
                        .body(APIResponseDTO.error("APPROVE_FAILED", (String) result.get("message"), null));
            }
        } catch (Exception e) {
            logger.error("Error approving access id={}: {}", logId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponseDTO.error("INTERNAL_ERROR", "Lỗi phê duyệt", e.getMessage()));
        }
    }

    

    @PostMapping("/batch-offline")
    public ResponseEntity<APIResponseDTO<Map<String, Integer>>> batchOfflineSync(
            @RequestBody List<Map<String, Object>> records) {
        
        int saved = 0;
        int skipped = 0;
        
        for (Map<String, Object> r : records) {
            try {
                
                String cccd = (String) r.getOrDefault("cccd", r.get("cccd"));
                String deviceCode = (String) r.getOrDefault("deviceCode",
                        r.getOrDefault("device_code", r.get("device_id")));

                if (cccd == null || cccd.isBlank()) {
                    logger.warn("batchOfflineSync: skip - missing cccd");
                    skipped++;
                    continue;
                }
                if (deviceCode == null || deviceCode.isBlank()) {
                    logger.warn("batchOfflineSync: skip - missing deviceCode for cccd={}", cccd);
                    skipped++;
                    continue;
                }

                
                LocalDateTime scanTime;
                try {
                    scanTime = LocalDateTime.parse((String) r.get("scanTime"));
                } catch (Exception ex) {
                    scanTime = LocalDateTime.now();
                }

                Double score = r.get("score") instanceof Number ? ((Number) r.get("score")).doubleValue() : 0.0;
                Boolean appMatched = r.get("matched") instanceof Boolean ? (Boolean) r.get("matched") :
                        Boolean.valueOf(String.valueOf(r.getOrDefault("matched", "false")));
                String method = String.valueOf(r.getOrDefault("method", r.getOrDefault("comparisonMethod", "OFFLINE_SYNC")));
                String selfieImage = (String) r.getOrDefault("imageLive",
                        r.getOrDefault("selfie", r.getOrDefault("selfieImage", "")));

                
                double backupThresh = 0.60;
                double primaryThresh = 0.60;
                boolean accessGranted;
                String finalStatus;
                if (score >= primaryThresh) {
                    accessGranted = true;
                    finalStatus = "SUCCESS";
                } else if (score >= backupThresh) {
                    
                    accessGranted = true;
                    finalStatus = "SUCCESS";
                    method = "GRAY_ZONE_OFFLINE_SYNC";
                    logger.info("batchOfflineSync: gray zone GRANT cccd={}, score={}", cccd, score);
                } else {
                    accessGranted = false;
                    finalStatus = "FAILED";
                }

                
                prj3.example.Prj3.entity.Employee employee = null;
                java.util.Optional<prj3.example.Prj3.entity.Employee> empOpt = employeeRepository.findByCccd(cccd);
                if (empOpt.isEmpty() && cccd.length() < 12) {
                    java.util.List<prj3.example.Prj3.entity.Employee> candidates = employeeRepository.findByCccdEndingWith(cccd);
                    if (candidates.size() == 1) empOpt = java.util.Optional.of(candidates.get(0));
                }
                String empName = (String) r.getOrDefault("capturedName", "");
                String fullCccd = cccd;
                if (empOpt.isPresent()) {
                    employee = empOpt.get();
                    empName = employee.getFullName();
                    fullCccd = employee.getCccd(); 
                }

                AttendanceLog log = AttendanceLog.builder()
                        .cccd(fullCccd)
                        .deviceCode(deviceCode)
                        .capturedName(empName)
                        .employeeName(empName)
                        .fullName(empName)
                        .score(score)
                        .matchScore(score)
                        .matched(accessGranted)
                        .accessGranted(accessGranted)
                        .comparisonMethod(method)
                        .comparisonMode(method)
                        .method(method)
                        .status(finalStatus)
                        .selfieImage(selfieImage)
                        .scanTime(scanTime)
                        .build();
                if (employee != null) log.setEmployee(employee);

                attendanceLogRepository.save(log);
                saved++;
            } catch (Exception e) {
                logger.warn("batchOfflineSync: skip record - {}", e.getMessage());
                skipped++;
            }
        }

        logger.info("batchOfflineSync: saved={}, skipped={}", saved, skipped);
        return ResponseEntity.ok(
                APIResponseDTO.ok(Map.of("saved", saved, "skipped", skipped),
                        "Đồng bộ offline hoàn tất: " + saved + " bản ghi"));
    }

    

    @GetMapping("/history")
    public ResponseEntity<APIResponseDTO<Page<AttendanceHistoryDTO>>> getAttendanceHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String cccd,
            @RequestParam(required = false) String deviceCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Double scoreMin,
            @RequestParam(required = false) Double scoreMax,
            @RequestParam(required = false) Boolean accessGranted) {
        
        logger.info("Fetching history - page: {}, size: {}, cccd: {}, device: {}", 
                   page, size, cccd, deviceCode);
        
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("scanTime").descending());
            
            LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
            LocalDateTime end = endDate != null ? endDate.plusDays(1).atStartOfDay() : null;

            Page<AttendanceLog> logs = attendanceLogRepository.findByFilters(
                    cccd, deviceCode, start, end, scoreMin, scoreMax, accessGranted, pageable);
            
            Page<AttendanceHistoryDTO> history = logs.map(this::convertToHistoryDTO);
            
            return ResponseEntity.ok(
                    APIResponseDTO.ok(history, "History fetched successfully")
            );
        } catch (Exception e) {
            logger.error("Error fetching history", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponseDTO.error("FETCH_ERROR", "Failed to fetch history", e.getMessage()));
        }
    }

    

    @GetMapping("/dashboard/stats")
    public ResponseEntity<APIResponseDTO<DashboardStatsDTO>> getDashboardStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        try {
            LocalDate queryDate = date != null ? date : LocalDate.now();
            LocalDateTime start = queryDate.atStartOfDay();
            LocalDateTime end = queryDate.plusDays(1).atStartOfDay();

            long successful = attendanceLogRepository.countByDateRangeAndStatusForAuthRate(start, end, true);
            long denied = attendanceLogRepository.countByDateRangeAndStatusForAuthRate(start, end, false);
            long total = successful + denied;
            double rate = total > 0 ? (successful * 100.0) / total : 0;
            
            long activeEmployees = employeeRepository.countByIsActive(true);
            long totalDevices = deviceRepository.count();
            
            DashboardStatsDTO stats = DashboardStatsDTO.builder()
                    .totalAttempts(total)
                    .successfulAttempts(successful)
                    .deniedAttempts(denied)
                    .successRate(rate)
                    .activeEmployees(activeEmployees)
                    .totalDevices(totalDevices)
                    .lastUpdated(LocalDateTime.now())
                    .build();
            
            return ResponseEntity.ok(
                    APIResponseDTO.ok(stats, "Dashboard stats retrieved")
            );
        } catch (Exception e) {
            logger.error("Error fetching dashboard stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponseDTO.error("STATS_ERROR", "Failed to fetch stats", e.getMessage()));
        }
    }

    

    @GetMapping("/dashboard/stats/overall")
    public ResponseEntity<APIResponseDTO<DashboardStatsDTO>> getOverallDashboardStats() {
        
        try {
            long successful = attendanceLogRepository.countByStatusForAuthRate(true);
            long denied = attendanceLogRepository.countByStatusForAuthRate(false);
            long total = successful + denied;
            double rate = total > 0 ? (successful * 100.0) / total : 0;
            
            long activeEmployees = employeeRepository.countByIsActive(true);
            long totalDevices = deviceRepository.count();
            long onlineDevices = totalDevices; 
            
            DashboardStatsDTO stats = DashboardStatsDTO.builder()
                    .totalAttempts(total)
                    .successfulAttempts(successful)
                    .deniedAttempts(denied)
                    .successRate(rate)
                    .activeEmployees(activeEmployees)
                    .totalDevices(totalDevices)
                    .onlineDevices(onlineDevices)
                    .lastUpdated(LocalDateTime.now())
                    .build();
            
            return ResponseEntity.ok(
                    APIResponseDTO.ok(stats, "Overall dashboard stats retrieved")
            );
        } catch (Exception e) {
            logger.error("Error fetching overall dashboard stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponseDTO.error("STATS_ERROR", "Failed to fetch overall stats", e.getMessage()));
        }
    }

    

    @GetMapping("/devices/{deviceCode}/stats")
    public ResponseEntity<APIResponseDTO<DeviceDashboardDTO>> getDeviceStats(
            @PathVariable String deviceCode) {
        
        try {
                Device device = deviceRepository.findByDeviceCode(deviceCode)
                    .orElseThrow(() -> new Exception("Device not found"));
            
            LocalDateTime dayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
            LocalDateTime dayEnd = dayStart.plusDays(1);
            LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            LocalDateTime monthEnd = monthStart.plusMonths(1);

                long todaySuccess = attendanceLogRepository.countByDeviceAndDateRangeAndStatusForAuthRate(
                    deviceCode, dayStart, dayEnd, true);
                long todayDenied = attendanceLogRepository.countByDeviceAndDateRangeAndStatusForAuthRate(
                    deviceCode, dayStart, dayEnd, false);
                long todayAttempts = todaySuccess + todayDenied;
            double successRate = todayAttempts > 0 ? (todaySuccess * 100.0) / todayAttempts : 0;

            long monthSuccess = attendanceLogRepository.countByDeviceAndDateRangeAndStatusForAuthRate(
                    deviceCode, monthStart, monthEnd, true);
            long monthDenied = attendanceLogRepository.countByDeviceAndDateRangeAndStatusForAuthRate(
                    deviceCode, monthStart, monthEnd, false);
            long monthAttempts = monthSuccess + monthDenied;
            double monthSuccessRate = monthAttempts > 0 ? (monthSuccess * 100.0) / monthAttempts : 0;
            
            DeviceDashboardDTO dto = DeviceDashboardDTO.builder()
                    .deviceCode(deviceCode)
                    .location(device.getLocation())
                    .status("ONLINE")
                    .lastSeen(LocalDateTime.now())
                    .todayAttempts(todayAttempts)
                    .todaySuccess(todaySuccess)
                    .todaySuccessRate(successRate)
                    .monthAttempts(monthAttempts)
                    .monthSuccess(monthSuccess)
                    .monthSuccessRate(monthSuccessRate)
                    .ipAddress(device.getIpAddress())
                    .build();
            
            return ResponseEntity.ok(
                    APIResponseDTO.ok(dto, "Device stats retrieved")
            );
        } catch (Exception e) {
            logger.error("Error fetching device stats", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(APIResponseDTO.error("DEVICE_NOT_FOUND", "Device not found", e.getMessage()));
        }
    }

    

    @PostMapping("/remote-unlock/{deviceCode}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'SUPER_ADMIN')")
    public ResponseEntity<APIResponseDTO<Map<String, Object>>> remoteUnlock(
            @PathVariable String deviceCode,
            @RequestBody(required = false) Map<String, String> body) {

        String reason = body != null ? body.getOrDefault("reason", null) : null;
        logger.info("Remote unlock requested - Device: {}, Reason: {}", deviceCode, reason);
        
        try {
            Map<String, Object> result = attendanceService.remoteUnlockDoor(deviceCode, reason);
            
            return ResponseEntity.ok(
                    APIResponseDTO.ok(result, "Unlock signal sent successfully")
            );
        } catch (Exception e) {
            logger.error("Error sending unlock signal", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponseDTO.error("UNLOCK_ERROR", "Failed to send unlock signal", e.getMessage()));
        }
    }

    

    @PostMapping("/instant-unlock/{deviceCode}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'SUPER_ADMIN')")
    public ResponseEntity<APIResponseDTO<Map<String, Object>>> instantUnlock(
            @PathVariable String deviceCode,
            @RequestBody(required = false) Map<String, String> body) {

        String reason = body != null ? body.getOrDefault("reason", "Admin mở cửa trực tiếp") : "Admin mở cửa trực tiếp";
        logger.info("Instant unlock requested - Device: {}, Reason: {}", deviceCode, reason);

        try {
            Map<String, Object> result = attendanceService.instantUnlockDoor(deviceCode, reason);
            return ResponseEntity.ok(APIResponseDTO.ok(result, "Door opened instantly"));
        } catch (Exception e) {
            logger.error("Error sending instant unlock", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponseDTO.error("UNLOCK_ERROR", "Failed to open door", e.getMessage()));
        }
    }

    

    @PostMapping("/remote-entry")
    public ResponseEntity<APIResponseDTO<Map<String, Object>>> remoteEntry(
            @RequestBody Map<String, String> body) {
        String deviceCode  = body.getOrDefault("deviceCode", "").trim();
        String visitorName = body.getOrDefault("visitorName", "").trim();
        String selfieImage = body.getOrDefault("selfieImage", "");
        String chipImage   = body.getOrDefault("chipImage", "");
        String cccdNumber  = body.getOrDefault("cccdNumber", "");

        if (deviceCode.isBlank() || visitorName.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(APIResponseDTO.error("INVALID_INPUT", "deviceCode và visitorName là bắt buộc", null));
        }
        if (chipImage.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(APIResponseDTO.error("INVALID_INPUT", "chipImage (ảnh chip CCCD) là bắt buộc", null));
        }
        if (selfieImage.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(APIResponseDTO.error("INVALID_INPUT", "selfieImage là bắt buộc", null));
        }

        logger.info("Remote entry - Device: {}, Visitor: {}, CCCD: {}", deviceCode, visitorName, cccdNumber);
        try {
            Map<String, Object> result = attendanceService.logRemoteEntry(
                    deviceCode, visitorName, selfieImage, chipImage, cccdNumber);

            if (Boolean.FALSE.equals(result.get("success"))) {
                
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(APIResponseDTO.error("FACE_MISMATCH", (String) result.get("message"), null));
            }
            return ResponseEntity.ok(APIResponseDTO.ok(result, "Xác minh thành công, cửa đã mở"));
        } catch (Exception e) {
            logger.error("Error in remote entry", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponseDTO.error("SERVER_ERROR", "Lỗi xử lý: " + e.getMessage(), null));
        }
    }

    

    @GetMapping("/employee/{cccd}/summary")
    public ResponseEntity<APIResponseDTO<EmployeeAttendanceDTO>> getEmployeeAttendance(
            @PathVariable String cccd,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {
        
        try {
            LocalDate queryMonth = month != null ? month : LocalDate.now().withDayOfMonth(1);
            LocalDateTime start = queryMonth.atStartOfDay();
            LocalDateTime end = queryMonth.plusMonths(1).atStartOfDay();
            
            Employee employee = employeeRepository.findByCccd(cccd)
                    .orElseThrow(() -> new Exception("Employee not found"));
            
            long totalDays = java.time.temporal.ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate());
            long presentDays = attendanceLogRepository.countDistinctDatesByCccdAndDateRange(cccd, start, end);
            long absentDays = totalDays - presentDays;
            double rate = totalDays > 0 ? (presentDays * 100.0) / totalDays : 0;
            
            LocalDateTime lastScan = attendanceLogRepository.findLastScanByCccd(cccd);
            
            EmployeeAttendanceDTO dto = EmployeeAttendanceDTO.builder()
                    .cccd(cccd)
                    .fullName(employee.getFullName())
                    .totalDays((int)totalDays)
                    .presentDays((int)presentDays)
                    .absentDays((int)absentDays)
                    .attendanceRate(rate)
                    .lastScan(lastScan)
                    .status(employee.getIsActive() ? "ACTIVE" : "INACTIVE")
                    .build();
            
            return ResponseEntity.ok(
                    APIResponseDTO.ok(dto, "Employee attendance retrieved")
            );
        } catch (Exception e) {
            logger.error("Error fetching employee attendance", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(APIResponseDTO.error("EMPLOYEE_NOT_FOUND", "Employee not found", e.getMessage()));
        }
    }

    

    @GetMapping("/special-cases")
    public ResponseEntity<APIResponseDTO<Page<AttendanceHistoryDTO>>> getSpecialCases(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String deviceCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("scanTime").descending());

            Page<AttendanceLog> logs = attendanceLogRepository.findRemoteUnlocks(deviceCode, pageable);

            Page<AttendanceHistoryDTO> result = logs.map(this::convertToHistoryDTO);
            return ResponseEntity.ok(APIResponseDTO.ok(result, "Special cases fetched"));
        } catch (Exception e) {
            logger.error("Error fetching special cases", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponseDTO.error("FETCH_ERROR", "Failed to fetch special cases", e.getMessage()));
        }
    }

    

    @GetMapping("/violations")
    public ResponseEntity<APIResponseDTO<Page<AttendanceHistoryDTO>>> getViolations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String deviceCode) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("scanTime").descending());
            Page<AttendanceLog> logs = attendanceLogRepository.findViolations(deviceCode, pageable);
            Page<AttendanceHistoryDTO> result = logs.map(this::convertToHistoryDTO);
            return ResponseEntity.ok(APIResponseDTO.ok(result, "Violations fetched"));
        } catch (Exception e) {
            logger.error("Error fetching violations", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponseDTO.error("FETCH_ERROR", "Failed to fetch violations", e.getMessage()));
        }
    }

    
    private AttendanceHistoryDTO convertToHistoryDTO(AttendanceLog log) {
        String status = log.getAlertType() != null ? log.getAlertType() :
                (Boolean.TRUE.equals(log.getAccessGranted()) ? "SUCCESS" : "DENIED");
        
        String cccd = log.getCccd();
        String fullName = log.getFullName();
        try {
            
            if (log.getEmployee() != null && log.getEmployee().getCccd() != null
                    && log.getEmployee().getCccd().length() == 12) {
                cccd = log.getEmployee().getCccd();
                if (fullName == null || fullName.isBlank()) fullName = log.getEmployee().getFullName();
            }
            
            if (cccd == null || cccd.length() < 12) {
                String suffix = cccd;
                if (suffix != null && !suffix.isBlank()) {
                    java.util.List<prj3.example.Prj3.entity.Employee> candidates =
                            employeeRepository.findByCccdEndingWith(suffix);
                    if (candidates.size() == 1) {
                        cccd = candidates.get(0).getCccd();
                        if (fullName == null || fullName.isBlank()) fullName = candidates.get(0).getFullName();
                    }
                }
            }
        } catch (Exception ignored) {  }
        return AttendanceHistoryDTO.builder()
                .id(log.getId())
                .cccd(cccd)
                .fullName(fullName)
                .deviceCode(log.getDeviceCode())
                .scanTime(log.getScanTime())
                .accessGranted(log.getAccessGranted())
                .matchScore(log.getMatchScore())
                .comparisonMethod(log.getComparisonMethod())
                .alertType(log.getAlertType())
                .status(status)
                .selfieImage(log.getSelfieImage())
                .build();
    }

    

    @GetMapping("/export/{month}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','VIEWER','HR_MANAGER','OPERATOR')")
    public ResponseEntity<byte[]> exportByMonth(@PathVariable String month) {
        try {
            
            LocalDate monthStart = LocalDate.parse(month + "-01");
            LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
            LocalDateTime start = monthStart.atStartOfDay();
            LocalDateTime end = monthEnd.plusDays(1).atStartOfDay();
            
            
            List<AttendanceLog> logs = attendanceLogRepository.findAll().stream()
                    .filter(log -> log.getScanTime() != null && 
                            !log.getScanTime().isBefore(start) && 
                            log.getScanTime().isBefore(end))
                    .sorted((a, b) -> b.getScanTime().compareTo(a.getScanTime()))
                    .collect(Collectors.toList());
            
            byte[] excelBytes = generateAttendanceExcel(logs, month);
            
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=attendance_" + month + ".xlsx")
                    .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .body(excelBytes);
        } catch (Exception e) {
            logger.error("Error exporting attendance", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    

    @GetMapping("/export/{deviceCode}/{month}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','VIEWER','HR_MANAGER','OPERATOR')")
    public ResponseEntity<byte[]> exportByDeviceMonth(
            @PathVariable String deviceCode,
            @PathVariable String month) {
        try {
            LocalDate monthStart = LocalDate.parse(month + "-01");
            LocalDateTime start = monthStart.atStartOfDay();
            LocalDateTime end = monthStart.plusMonths(1).atStartOfDay();

            List<AttendanceLog> logs = attendanceLogRepository.findAll().stream()
                    .filter(log -> log.getScanTime() != null &&
                            deviceCode.equals(log.getDeviceCode()) &&
                            !log.getScanTime().isBefore(start) &&
                            log.getScanTime().isBefore(end) &&
                            Boolean.TRUE.equals(log.getMatched()))
                    .sorted((a, b) -> b.getScanTime().compareTo(a.getScanTime()))
                    .collect(Collectors.toList());

            byte[] excelBytes = generateAttendanceExcel(logs, month);

            String filename = "attendance_" + deviceCode + "_" + month + ".xlsx";
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=" + filename)
                    .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .body(excelBytes);
        } catch (Exception e) {
            logger.error("Error exporting attendance for device {}", deviceCode, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    

    private byte[] generateAttendanceExcel(List<AttendanceLog> logs, String month) throws IOException {
        Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Attendance - " + month);
        
        
        Row headerRow = sheet.createRow(0);
        String[] columns = {"CCCD", "Full Name", "Device Code", "Scan Time", "Match Score", "Status", "Access Granted"};
        
        for (int i = 0; i < columns.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns[i]);
            org.apache.poi.ss.usermodel.CellStyle style = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            cell.setCellStyle(style);
        }
        
        
        int rowNum = 1;
        for (AttendanceLog log : logs) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(log.getCccd());
            row.createCell(1).setCellValue(log.getFullName() != null ? log.getFullName() : "");
            row.createCell(2).setCellValue(log.getDeviceCode());
            row.createCell(3).setCellValue(log.getScanTime() != null ? log.getScanTime().toString() : "");
            row.createCell(4).setCellValue(log.getMatchScore() != null ? log.getMatchScore() : 0);
            row.createCell(5).setCellValue(log.getMatched() != null && log.getMatched() ? "MATCHED" : "NOT_MATCHED");
            row.createCell(6).setCellValue(log.getAccessGranted() != null && log.getAccessGranted() ? "Granted" : "Denied");
        }
        
        
        for (int i = 0; i < columns.length; i++) {
            sheet.autoSizeColumn(i);
        }
        
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        workbook.write(baos);
        workbook.close();
        
        return baos.toByteArray();
    }
}

