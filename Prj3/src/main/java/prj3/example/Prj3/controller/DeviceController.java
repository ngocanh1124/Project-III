package prj3.example.Prj3.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import prj3.example.Prj3.dto.*;
import prj3.example.Prj3.entity.Device;
import prj3.example.Prj3.entity.Employee;
import prj3.example.Prj3.repository.DeviceRepository;
import prj3.example.Prj3.service.AttendanceService;
import prj3.example.Prj3.service.EmployeeService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/device")

public class DeviceController {

    private static final Logger logger = LoggerFactory.getLogger(DeviceController.class);

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private EmployeeService employeeService;

    

    @PostMapping("/{deviceCode}/event")
    public ResponseEntity<APIResponseDTO<AttendanceRecordResponseDTO>> handleDeviceEvent(
            @PathVariable String deviceCode,
            @RequestBody DeviceEventDTO event
    ) {
        try {
            logger.info("Device event received from {}: cccd={}, score={}, matched={}",
                    deviceCode, event.getCccd(), event.getScore(), event.getMatched());

            Device device = deviceRepository.findByDeviceCode(deviceCode)
                    .orElseThrow(() -> new RuntimeException("Device not found: " + deviceCode));

            Long orgId = device.getOrganization() != null ? device.getOrganization().getId() : null;
            if (orgId == null) {
                return ResponseEntity.badRequest()
                        .body(APIResponseDTO.error("NO_ORG", "Device has no organization", "Device must belong to an organization."));
            }

            AttendanceRecordRequestDTO request = AttendanceRecordRequestDTO.builder()
                    .deviceCode(deviceCode)
                    .organizationId(orgId)
                    .cccd(event.getCccd() != null ? event.getCccd() : "")
                    .capturedName(event.getCapturedName())
                    .chipImage(event.getChipImageBase64() != null ? event.getChipImageBase64() : "")
                    .imageLive(event.getImageLiveBase64() != null ? event.getImageLiveBase64() : "")
                    .score(event.getScore() != null ? event.getScore() : 0.0)
                    .matched(event.getMatched() != null ? event.getMatched() : false)
                    .method(event.getMethod() != null ? event.getMethod() : "APP_OFFLINE")
                    .timestamp(event.getTimestamp() != null ? event.getTimestamp() : LocalDateTime.now())
                    .build();

            AttendanceRecordResponseDTO response = attendanceService.processAttendanceWithLayer2Validation(request);

            if (response.getAccessGranted()) {
                return ResponseEntity.ok(APIResponseDTO.ok(response, "Attendance accepted"));
            } else {
                return ResponseEntity.status(403).body(APIResponseDTO.error("ACCESS_DENIED", response.getAuthorizationReason(), "Access denied by server"));
            }
        } catch (Exception e) {
            logger.error("Error processing device event", e);
            return ResponseEntity.status(500).body(APIResponseDTO.error("INTERNAL_ERROR", "Failed to process device event", e.getMessage()));
        }
    }

    

    @GetMapping("/{deviceCode}/whitelist")
    public ResponseEntity<APIResponseDTO<List<EmployeeDTO>>> getDeviceWhitelist(@PathVariable String deviceCode) {
        try {
            List<Employee> employees = employeeService.getEmployeesByDevice(deviceCode);
            List<EmployeeDTO> dtos = employees.stream()
                    .map(employeeService::convertToDTO)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(APIResponseDTO.ok(dtos, "Whitelist retrieved"));
        } catch (Exception e) {
            logger.error("Error fetching whitelist for device {}", deviceCode, e);
            return ResponseEntity.status(500).body(APIResponseDTO.error("WHITELIST_ERROR", "Failed to fetch whitelist", e.getMessage()));
        }
    }

    

    @PostMapping("/{deviceCode}/sync")
    public ResponseEntity<APIResponseDTO<String>> syncDevice(@PathVariable String deviceCode) {
        try {
            attendanceService.publishSyncData(deviceCode);
            return ResponseEntity.ok(APIResponseDTO.ok("OK", "Đã gửi SYNC_DATA tới thiết bị " + deviceCode));
        } catch (Exception e) {
            logger.error("Error syncing device {}", deviceCode, e);
            return ResponseEntity.status(500).body(APIResponseDTO.error("SYNC_ERROR", "Lỗi sync thiết bị", e.getMessage()));
        }
    }
}
