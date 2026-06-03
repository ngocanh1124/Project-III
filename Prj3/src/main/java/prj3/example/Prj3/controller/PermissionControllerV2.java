package prj3.example.Prj3.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import prj3.example.Prj3.dto.APIResponseDTO;
import prj3.example.Prj3.entity.AccessPermission;
import prj3.example.Prj3.entity.Device;
import prj3.example.Prj3.entity.Employee;
import prj3.example.Prj3.repository.AccessPermissionRepository;
import prj3.example.Prj3.repository.DeviceRepository;
import prj3.example.Prj3.repository.EmployeeRepository;
import prj3.example.Prj3.service.AttendanceService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v2/permissions")
public class PermissionControllerV2 {

    private static final Logger logger = LoggerFactory.getLogger(PermissionControllerV2.class);

    @Autowired
    private AccessPermissionRepository permissionRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private AttendanceService attendanceService;

    
    @GetMapping
    public ResponseEntity<APIResponseDTO<List<Map<String, Object>>>> getPermissions(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long deviceId) {
        try {
            List<AccessPermission> permissions;
            if (employeeId != null && deviceId != null) {
                permissions = permissionRepository.findByEmployeeIdAndDeviceId(employeeId, deviceId);
            } else if (employeeId != null) {
                permissions = permissionRepository.findByEmployeeId(employeeId);
            } else if (deviceId != null) {
                permissions = permissionRepository.findByDeviceId(deviceId);
            } else {
                permissions = permissionRepository.findAll();
            }

            List<Map<String, Object>> dtos = permissions.stream()
                    .map(this::toMap)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(APIResponseDTO.ok(dtos, "Permissions fetched"));
        } catch (Exception e) {
            logger.error("Error fetching permissions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponseDTO.error("FETCH_ERROR", "Failed to fetch permissions", e.getMessage()));
        }
    }

    
    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<APIResponseDTO<Map<String, Object>>> createPermission(
            @RequestBody Map<String, Object> request) {
        try {
            Long employeeId = getLong(request, "employeeId");
            Long deviceId = getLong(request, "deviceId");

            Employee employee = employeeRepository.findById(employeeId)
                    .orElseThrow(() -> new RuntimeException("Employee not found: " + employeeId));
            Device device = deviceRepository.findById(deviceId)
                    .orElseThrow(() -> new RuntimeException("Device not found: " + deviceId));

            
            if (permissionRepository.existsByEmployeeIdAndDeviceId(employeeId, deviceId)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(APIResponseDTO.error("DUPLICATE", "Nhân viên này đã được gán vào cửa đó rồi", null));
            }

            AccessPermission permission = new AccessPermission();
            permission.setEmployee(employee);
            permission.setDevice(device);
            permission.setIsActive(true);

            if (request.containsKey("startDate"))
                permission.setStartDate(LocalDate.parse((String) request.get("startDate")));
            if (request.containsKey("endDate"))
                permission.setEndDate(LocalDate.parse((String) request.get("endDate")));
            if (request.containsKey("startTime"))
                permission.setStartTime(LocalTime.parse((String) request.get("startTime")));
            if (request.containsKey("endTime"))
                permission.setEndTime(LocalTime.parse((String) request.get("endTime")));

            AccessPermission saved = permissionRepository.save(permission);

            
            try {
                attendanceService.publishSyncData(device.getDeviceCode());
                logger.info("Auto-sync triggered after create permission - device: {}", device.getDeviceCode());
            } catch (Exception syncEx) {
                logger.warn("SYNC after create permission failed (non-critical): {}", syncEx.getMessage());
            }

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(APIResponseDTO.ok(toMap(saved), "Permission created"));
        } catch (Exception e) {
            logger.error("Error creating permission", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponseDTO.error("CREATE_ERROR", "Failed to create permission", e.getMessage()));
        }
    }

    
    @PutMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<APIResponseDTO<Map<String, Object>>> updatePermission(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        try {
            AccessPermission permission = permissionRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Permission not found"));

            if (request.containsKey("startDate"))
                permission.setStartDate(LocalDate.parse((String) request.get("startDate")));
            if (request.containsKey("endDate"))
                permission.setEndDate(LocalDate.parse((String) request.get("endDate")));
            if (request.containsKey("startTime"))
                permission.setStartTime(LocalTime.parse((String) request.get("startTime")));
            if (request.containsKey("endTime"))
                permission.setEndTime(LocalTime.parse((String) request.get("endTime")));
            if (request.containsKey("isActive"))
                permission.setIsActive(Boolean.parseBoolean(request.get("isActive").toString()));

            AccessPermission saved = permissionRepository.save(permission);

            
            if (saved.getDevice() != null) {
                try {
                    attendanceService.publishSyncData(saved.getDevice().getDeviceCode());
                    logger.info("Auto-sync triggered after update permission - device: {}", saved.getDevice().getDeviceCode());
                } catch (Exception syncEx) {
                    logger.warn("SYNC after update permission failed (non-critical): {}", syncEx.getMessage());
                }
            }

            return ResponseEntity.ok(APIResponseDTO.ok(toMap(saved), "Permission updated"));
        } catch (Exception e) {
            logger.error("Error updating permission", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponseDTO.error("UPDATE_ERROR", "Failed to update permission", e.getMessage()));
        }
    }

    
    @DeleteMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<APIResponseDTO<Void>> deletePermission(@PathVariable Long id) {
        try {
            
            String deviceCode = permissionRepository.findById(id)
                    .map(p -> p.getDevice() != null ? p.getDevice().getDeviceCode() : null)
                    .orElse(null);

            permissionRepository.deleteById(id);

            
            if (deviceCode != null) {
                try {
                    attendanceService.publishSyncData(deviceCode);
                    logger.info("Auto-sync triggered after delete permission - device: {}", deviceCode);
                } catch (Exception syncEx) {
                    logger.warn("SYNC after delete permission failed (non-critical): {}", syncEx.getMessage());
                }
            }

            return ResponseEntity.ok(APIResponseDTO.ok(null, "Permission deleted"));
        } catch (Exception e) {
            logger.error("Error deleting permission", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponseDTO.error("DELETE_ERROR", "Failed to delete permission", e.getMessage()));
        }
    }

    private Map<String, Object> toMap(AccessPermission p) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", p.getId());
        map.put("isActive", p.getIsActive());
        map.put("startDate", p.getStartDate() != null ? p.getStartDate().toString() : null);
        map.put("endDate", p.getEndDate() != null ? p.getEndDate().toString() : null);
        map.put("startTime", p.getStartTime() != null ? p.getStartTime().toString() : null);
        map.put("endTime", p.getEndTime() != null ? p.getEndTime().toString() : null);
        if (p.getEmployee() != null) {
            map.put("employeeId", p.getEmployee().getId());
            map.put("employeeName", p.getEmployee().getFullName());
            map.put("cccd", p.getEmployee().getCccd());
        }
        if (p.getDevice() != null) {
            map.put("deviceId", p.getDevice().getId());
            map.put("deviceCode", p.getDevice().getDeviceCode());
            map.put("locationName", p.getDevice().getLocationName());
        }
        return map;
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        return Long.parseLong(val.toString());
    }
}
