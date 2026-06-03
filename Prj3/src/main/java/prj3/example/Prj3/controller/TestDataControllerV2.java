package prj3.example.Prj3.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import prj3.example.Prj3.dto.*;
import prj3.example.Prj3.entity.*;
import prj3.example.Prj3.repository.*;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/v2/test")
public class TestDataControllerV2 {
    
    @Autowired
    private EmployeeRepository employeeRepository;
    
    @Autowired
    private AttendanceLogRepository attendanceLogRepository;
    
    @Autowired
    private DeviceRepository deviceRepository;
    
    @Autowired
    private OrganizationRepository organizationRepository;
    
    @Autowired
    private AccessPermissionRepository accessPermissionRepository;
    
    

    @PostMapping("/populate-employees")
    public ResponseEntity<?> populateEmployees(
            @RequestParam(defaultValue = "50") int count) {
        try {
            
            Organization org = organizationRepository.findById(1L)
                .orElse(Organization.builder()
                    .id(1L)
                    .name("Test Organization")
                    .address("123 Test Street")
                    .phone("0123456789")
                    .build());
            
            organizationRepository.save(org);
            
            List<Employee> employees = new ArrayList<>();
            String[] departments = {"HR", "IT", "Sales", "Marketing", "Finance", "Operations"};
            String[] positions = {"Manager", "Executive", "Staff", "Engineer", "Analyst"};
            
            for (int i = 1; i <= count; i++) {
                
                String cccd = String.format("07101234%04d", i);
                
                
                if (employeeRepository.findByCccd(cccd).isPresent()) {
                    continue;
                }
                
                Employee employee = Employee.builder()
                    .cccd(cccd)
                    .fullName("Test Employee " + i)
                    .department(departments[i % departments.length])
                    .position(positions[i % positions.length])
                    .email("emp" + i + "@company.com")
                    .phone("090" + String.format("%07d", i * 10))
                    .organization(org)
                    .isActive(true)
                    .createdAt(LocalDateTime.now())
                    
                    .faceVector(generateDummyFaceVector())
                    .imageRawUrl(java.util.Base64.getEncoder().encodeToString(generateDummyImage()))
                    .build();
                    
                employees.add(employee);
            }
            
            employeeRepository.saveAll(employees);
            
            log.info("Created {} test employees", employees.size());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Test employees created successfully",
                "count", employees.size(),
                "timestamp", LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("Error populating test employees", e);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "success", false,
                    "message", "Error: " + e.getMessage(),
                    "timestamp", LocalDateTime.now()
                ));
        }
    }
    
    

    @PostMapping("/populate-devices")
    public ResponseEntity<?> populateDevices() {
        try {
            List<Device> devices = new ArrayList<>();
            
            String[] locations = {"Main Gate", "Front Door", "Back Door", "IT Room", "Server Room", "Meeting Room A"};
            String[] deviceCodes = {"DEV001", "DEV002", "DEV003", "DEV004", "DEV005", "DEV006"};
            
            for (int i = 0; i < locations.length; i++) {
                if (deviceRepository.findByDeviceCode(deviceCodes[i]).isPresent()) {
                    continue;
                }
                
                Device device = Device.builder()
                    .deviceCode(deviceCodes[i])
                    .locationName(locations[i])
                    .location(locations[i])
                    .isOnline(true)
                    .lastSync(LocalDateTime.now())
                    .build();
                    
                devices.add(device);
            }
            
            deviceRepository.saveAll(devices);
            
            log.info("Created {} test devices", devices.size());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Test devices created successfully",
                "count", devices.size(),
                "timestamp", LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("Error populating test devices", e);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "success", false,
                    "message", "Error: " + e.getMessage(),
                    "timestamp", LocalDateTime.now()
                ));
        }
    }
    
    

    @PostMapping("/populate-permissions")
    public ResponseEntity<?> populatePermissions() {
        try {
            List<Employee> employees = employeeRepository.findAll();
            List<Device> devices = deviceRepository.findAll();
            
            if (employees.isEmpty() || devices.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Please create employees and devices first",
                    "timestamp", LocalDateTime.now()
                ));
            }
            
            List<AccessPermission> permissions = new ArrayList<>();
            LocalDate now = LocalDate.now();
            LocalDate tomorrow = now.plusDays(1);
            
            
            for (Employee emp : employees) {
                if (Math.random() < 0.8) {  
                    for (Device device : devices) {
                        if (Math.random() < 0.7) {  
                            
                            boolean exists = accessPermissionRepository.findAll().stream()
                                .anyMatch(p -> p.getEmployee().getCccd().equals(emp.getCccd()) && 
                                              p.getDevice().getDeviceCode().equals(device.getDeviceCode()));
                            
                            if (!exists) {
                                AccessPermission perm = AccessPermission.builder()
                                    .employee(emp)
                                    .device(device)
                                    .startDate(tomorrow)
                                    .startTime(java.time.LocalTime.of(7, 0, 0))
                                    .isActive(true)
                                    .build();
                                    
                                permissions.add(perm);
                            }
                        }
                    }
                }
            }
            
            accessPermissionRepository.saveAll(permissions);
            
            log.info("Created {} test access permissions", permissions.size());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Test permissions created successfully",
                "count", permissions.size(),
                "timestamp", LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("Error populating test permissions", e);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "success", false,
                    "message", "Error: " + e.getMessage(),
                    "timestamp", LocalDateTime.now()
                ));
        }
    }
    
    

    @PostMapping("/populate-attendance")
    public ResponseEntity<?> populateAttendance(
            @RequestParam(defaultValue = "200") int count) {
        try {
            List<Employee> employees = employeeRepository.findAll();
            List<Device> devices = deviceRepository.findAll();
            
            if (employees.isEmpty() || devices.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Please create employees and devices first",
                    "timestamp", LocalDateTime.now()
                ));
            }
            
            List<AttendanceLog> records = new ArrayList<>();
            LocalDate today = LocalDate.now();
            
            for (int i = 0; i < count; i++) {
                Employee emp = employees.get(i % employees.size());
                Device device = devices.get(i % devices.size());
                
               LocalDateTime recordTime = LocalDateTime.of(
                    today.minusDays(i / (employees.size() * devices.size())),
                    java.time.LocalTime.of(7 + (i % 10), i % 60, 0)
                );
                
                AttendanceLog attendanceLog = AttendanceLog.builder()
                    .cccd(emp.getCccd())
                    .deviceCode(device.getDeviceCode())
                    .scanTime(recordTime)
                    .score(0.85 + (Math.random() * 0.15))  
                    .matchScore(0.85 + (Math.random() * 0.15))
                    .matched(true)
                    .accessGranted(true)
                    .status("SUCCESS")
                    .comparisonMethod("APP_OFFLINE")
                    .build();
                    
                records.add(attendanceLog);
            }
            
            attendanceLogRepository.saveAll(records);
            
            log.info("Created {} test attendance records", records.size());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Test attendance records created successfully",
                "count", records.size(),
                "timestamp", LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("Error populating test attendance", e);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "success", false,
                    "message", "Error: " + e.getMessage(),
                    "timestamp", LocalDateTime.now()
                ));
        }
    }
    
    

    @PostMapping("/populate-all")
    public ResponseEntity<?> populateAll() {
        try {
            log.info("Starting full test data population...");
            
            
            var empResult = populateEmployees(50);
            
            
            var devResult = populateDevices();
            
            
            var permResult = populatePermissions();
            
            
            var attResult = populateAttendance(200);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "All test data populated successfully",
                "steps", List.of("50 employees", "6 devices", "permissions", "200 attendance records"),
                "timestamp", LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("Error in full population", e);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "success", false,
                    "message", "Error: " + e.getMessage(),
                    "timestamp", LocalDateTime.now()
                ));
        }
    }
    
    

    @DeleteMapping("/clear-all")
    public ResponseEntity<?> clearAllData() {
        try {
            log.warn("CLEARING ALL TEST DATA - This cannot be undone!");
            
            int attCount = (int) attendanceLogRepository.count();
            int permCount = (int) accessPermissionRepository.count();
            int empCount = (int) employeeRepository.count();
            int devCount = (int) deviceRepository.count();
            
            attendanceLogRepository.deleteAll();
            accessPermissionRepository.deleteAll();
            employeeRepository.deleteAll();
            deviceRepository.deleteAll();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "All test data cleared successfully",
                "cleared", Map.of(
                    "attendanceRecords", attCount,
                    "permissions", permCount,
                    "employees", empCount,
                    "devices", devCount
                ),
                "timestamp", LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("Error clearing test data", e);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "success", false,
                    "message", "Error: " + e.getMessage(),
                    "timestamp", LocalDateTime.now()
                ));
        }
    }
    
    

    @GetMapping("/stats")
    public ResponseEntity<?> getTestDataStats() {
        try {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                    "employees", employeeRepository.count(),
                    "devices", deviceRepository.count(),
                    "permissions", accessPermissionRepository.count(),
                    "attendanceRecords", attendanceLogRepository.count()
                ),
                "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Error fetching stats", e);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "success", false,
                    "message", "Error: " + e.getMessage(),
                    "timestamp", LocalDateTime.now()
                ));
        }
    }
    
    
    
    private String generateDummyFaceVector() {
        
        byte[] vector = new byte[2048];
        new Random().nextBytes(vector);
        return java.util.Base64.getEncoder().encodeToString(vector);
    }
    
    private byte[] generateDummyImage() {
        
        byte[] image = new byte[5000];
        new Random().nextBytes(image);
        return image;
    }
}
