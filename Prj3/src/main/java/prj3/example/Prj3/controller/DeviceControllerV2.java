package prj3.example.Prj3.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import prj3.example.Prj3.dto.APIResponseDTO;
import prj3.example.Prj3.entity.Device;
import prj3.example.Prj3.entity.Organization;
import prj3.example.Prj3.repository.DeviceRepository;
import prj3.example.Prj3.repository.OrganizationRepository;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/devices")
public class DeviceControllerV2 {

    private static final Logger logger = LoggerFactory.getLogger(DeviceControllerV2.class);

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    
    @GetMapping
    public ResponseEntity<APIResponseDTO<Page<Map<String, Object>>>> getDevices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
            Page<Device> devices;

            if (search != null && !search.isBlank()) {
                devices = deviceRepository.findByDeviceCodeContainingOrLocationNameContaining(search, search, pageable);
            } else {
                devices = deviceRepository.findAll(pageable);
            }

            Page<Map<String, Object>> dtos = devices.map(this::toMap);
            return ResponseEntity.ok(APIResponseDTO.ok(dtos, "Devices fetched"));
        } catch (Exception e) {
            logger.error("Error fetching devices", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponseDTO.error("FETCH_ERROR", "Failed to fetch devices", e.getMessage()));
        }
    }

    
    @GetMapping("/{id}")
    public ResponseEntity<APIResponseDTO<Map<String, Object>>> getDevice(@PathVariable Long id) {
        try {
            Device device = deviceRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Device not found"));
            return ResponseEntity.ok(APIResponseDTO.ok(toMap(device), "Device found"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(APIResponseDTO.error("NOT_FOUND", "Device not found", e.getMessage()));
        }
    }

    
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<APIResponseDTO<Map<String, Object>>> createDevice(
            @RequestBody Map<String, Object> request) {
        try {
            Device device = new Device();
            device.setDeviceCode((String) request.get("deviceCode"));
            device.setLocationName((String) request.getOrDefault("locationName", ""));
            device.setLocation((String) request.getOrDefault("locationName", ""));
            device.setIpAddress((String) request.get("ipAddress"));
            device.setIsOnline(false);
            device.setLastSync(LocalDateTime.now());

            
            Object orgId = request.get("organizationId");
            if (orgId != null) {
                Long orgIdLong = Long.parseLong(orgId.toString());
                organizationRepository.findById(orgIdLong).ifPresent(device::setOrganization);
            }

            Device saved = deviceRepository.save(device);
            logger.info("Device created: {}", saved.getDeviceCode());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(APIResponseDTO.ok(toMap(saved), "Device created successfully"));
        } catch (Exception e) {
            logger.error("Error creating device", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponseDTO.error("CREATE_ERROR", "Failed to create device", e.getMessage()));
        }
    }

    
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<APIResponseDTO<Map<String, Object>>> updateDevice(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        try {
            Device device = deviceRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Device not found"));

            if (request.containsKey("deviceCode")) device.setDeviceCode((String) request.get("deviceCode"));
            if (request.containsKey("locationName")) {
                device.setLocationName((String) request.get("locationName"));
                device.setLocation((String) request.get("locationName"));
            }
            if (request.containsKey("ipAddress")) device.setIpAddress((String) request.get("ipAddress"));
            if (request.containsKey("isOnline")) {
                device.setIsOnline(Boolean.parseBoolean(request.get("isOnline").toString()));
            }

            Device saved = deviceRepository.save(device);
            return ResponseEntity.ok(APIResponseDTO.ok(toMap(saved), "Device updated successfully"));
        } catch (Exception e) {
            logger.error("Error updating device", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponseDTO.error("UPDATE_ERROR", "Failed to update device", e.getMessage()));
        }
    }

    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<APIResponseDTO<Void>> deleteDevice(@PathVariable Long id) {
        try {
            deviceRepository.deleteById(id);
            logger.info("Device deleted: id={}", id);
            return ResponseEntity.ok(APIResponseDTO.ok(null, "Device deleted successfully"));
        } catch (Exception e) {
            logger.error("Error deleting device", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponseDTO.error("DELETE_ERROR", "Failed to delete device", e.getMessage()));
        }
    }

    private Map<String, Object> toMap(Device device) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", device.getId());
        map.put("deviceCode", device.getDeviceCode());
        map.put("locationName", device.getLocationName() != null ? device.getLocationName() : device.getLocation());
        map.put("ipAddress", device.getIpAddress());
        map.put("isOnline", device.getIsOnline());
        map.put("lastSync", device.getLastSync());
        if (device.getOrganization() != null) {
            map.put("organizationId", device.getOrganization().getId());
            map.put("organizationName", device.getOrganization().getName());
        }
        return map;
    }
}
