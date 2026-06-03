package prj3.example.Prj3.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import prj3.example.Prj3.dto.EmployeeDTO;
import prj3.example.Prj3.dto.EmployeeCreateDTO;
import prj3.example.Prj3.dto.EmployeeUpdateDTO;
import prj3.example.Prj3.entity.Employee;
import prj3.example.Prj3.entity.Organization;
import prj3.example.Prj3.entity.Device;
import prj3.example.Prj3.entity.AccessPermission;
import prj3.example.Prj3.repository.EmployeeRepository;
import prj3.example.Prj3.repository.OrganizationRepository;
import prj3.example.Prj3.repository.DeviceRepository;
import prj3.example.Prj3.repository.AccessPermissionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EmployeeService {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private AccessPermissionRepository accessPermissionRepository;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private AttendanceService attendanceService;

    @Autowired
    private FaceAIService faceAIService;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    

    public Employee createEmployee(EmployeeCreateDTO dto) {
        if (employeeRepository.findByCccd(dto.getCccd()).isPresent()) {
            throw new IllegalArgumentException("Nhân viên với CCCD '" + dto.getCccd() + "' đã tồn tại trong hệ thống.");
        }

        Organization organization;
        if (dto.getOrganizationId() != null) {
            organization = organizationRepository.findById(dto.getOrganizationId())
                    .orElseThrow(() -> new RuntimeException("Organization not found"));
        } else {
            organization = organizationRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new RuntimeException("Chưa có tổ chức nào trong hệ thống. Vui lòng tạo tổ chức trước."));
        }

        Employee employee = new Employee();
        employee.setCccd(dto.getCccd());
        employee.setFullName(dto.getFullName());
        employee.setDepartment(dto.getDepartment());
        employee.setPosition(dto.getPosition());
        employee.setEmail(dto.getEmail());
        employee.setPhone(dto.getPhone());
        employee.setFaceVector(dto.getFaceVectorBase64());
        employee.setImageRawUrl(dto.getBackupPhotoBase64());
        employee.setOrganization(organization);
        employee.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        employee.setCreatedAt(LocalDateTime.now());

        return employeeRepository.save(employee);
    }

    

    public Optional<Employee> getEmployeeById(Long id) {
        return employeeRepository.findById(id);
    }

    

    public Optional<Employee> getEmployeeByCccd(String cccd) {
        return employeeRepository.findByCccd(cccd);
    }

    

    public Page<Employee> searchEmployees(String searchTerm, Pageable pageable) {
        return employeeRepository.findByCccdOrFullNameContaining(searchTerm, searchTerm, pageable);
    }

    

    public List<Employee> getActiveEmployees() {
        return employeeRepository.findByIsActive(true);
    }

    

    public Page<Employee> getActiveEmployees(Pageable pageable) {
        return employeeRepository.findByIsActive(true, pageable);
    }

    

    public Employee updateEmployee(String cccd, EmployeeUpdateDTO dto) {
        Employee employee = employeeRepository.findByCccd(cccd)
                .orElseThrow(() -> new RuntimeException("Employee not found with CCCD: " + cccd));

        if (dto.getFullName() != null) {
            employee.setFullName(dto.getFullName());
        }
        if (dto.getDepartment() != null) {
            employee.setDepartment(dto.getDepartment());
        }
        if (dto.getPosition() != null) {
            employee.setPosition(dto.getPosition());
        }
        if (dto.getEmail() != null) {
            employee.setEmail(dto.getEmail());
        }
        if (dto.getPhone() != null) {
            employee.setPhone(dto.getPhone());
        }
        if (dto.getFaceVectorBase64() != null) {
            employee.setFaceVector(dto.getFaceVectorBase64());
        }
        if (dto.getBackupPhotoBase64() != null) {
            employee.setImageRawUrl(dto.getBackupPhotoBase64());
        }
        if (dto.getIsActive() != null) {
            employee.setIsActive(dto.getIsActive());
        }

        return employeeRepository.save(employee);
    }

    

    public void deleteEmployee(String cccd) {
        Employee employee = employeeRepository.findByCccd(cccd)
                .orElseThrow(() -> new RuntimeException("Employee not found with CCCD: " + cccd));
        employeeRepository.delete(employee);
    }

    

    public Page<Employee> getAllEmployees(Pageable pageable) {
        return employeeRepository.findAll(pageable);
    }

    

    public EmployeeDTO convertToDTO(Employee employee) {
        return EmployeeDTO.builder()
                .cccd(employee.getCccd())
                .fullName(employee.getFullName())
                .department(employee.getDepartment())
                .position(employee.getPosition())
                .email(employee.getEmail())
                .phone(employee.getPhone())
                .isActive(employee.getIsActive())
                .imageRawUrl(employee.getImageRawUrl())
                .createdAt(employee.getCreatedAt())
                .build();
    }

    

    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> importFromExcel(MultipartFile file, Long organizationId) throws IOException {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Map<String, Object> result = new HashMap<>();
        List<String> successList = new ArrayList<>();
        List<String> failureList = new ArrayList<>();
        int lastRowNum = 0;

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            lastRowNum = sheet.getLastRowNum();
            
            for (int i = 1; i <= lastRowNum; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    
                    
                    
                    
                    
                    
                    
                    org.apache.poi.ss.usermodel.DataFormatter fmt = new org.apache.poi.ss.usermodel.DataFormatter();
                    String cccdRaw = row.getCell(0) != null ? fmt.formatCellValue(row.getCell(0)).trim() : "";
                    
                    String cccd = cccdRaw.replaceAll("[^0-9]", ""); 
                    if (!cccd.isEmpty() && cccd.length() < 12) {
                        cccd = String.format("%012d", Long.parseLong(cccd));
                    }
                    String fullName = row.getCell(1) != null ? getCellString(row.getCell(1)) : "";
                    
                    String department = row.getCell(4) != null ? getCellString(row.getCell(4)) : "";
                    String position   = row.getCell(5) != null ? getCellString(row.getCell(5)) : "";
                    String email      = row.getCell(6) != null ? getCellString(row.getCell(6)) : null;
                    String phone      = row.getCell(7) != null ? getCellString(row.getCell(7)) : null;
                    
                    String doorCodes  = row.getCell(8) != null ? getCellString(row.getCell(8)) : null;
                    
                    String startTimeStr = row.getCell(9)  != null ? getCellString(row.getCell(9)).trim()  : null;
                    String endTimeStr   = row.getCell(10) != null ? getCellString(row.getCell(10)).trim() : null;
                    String startDateStr = row.getCell(11) != null ? getCellString(row.getCell(11)).trim() : null;
                    String endDateStr   = row.getCell(12) != null ? getCellString(row.getCell(12)).trim() : null;
                    
                    String imageBase64 = null;

                    if (cccd.isBlank() || fullName.isBlank()) {
                        failureList.add("Row " + (i + 1) + ": CCCD hoac Ho ten trong");
                        continue;
                    }

                    
                    java.util.Optional<Employee> existingOpt = employeeRepository.findByCccd(cccd);
                    if (!existingOpt.isPresent() && cccd.length() == 12 && cccd.startsWith("0")) {
                        
                        String unpaddedCccd = cccd.replaceFirst("^0+", "");
                        existingOpt = employeeRepository.findByCccd(unpaddedCccd);
                        if (existingOpt.isPresent()) {
                            
                            existingOpt.get().setCccd(cccd);
                            employeeRepository.save(existingOpt.get());
                            log.info("Excel import: Normalized CCCD from '{}' to '{}'", unpaddedCccd, cccd);
                        }
                    }
                    boolean isExistingEmployee = existingOpt.isPresent();
                    Employee employee;
                    if (isExistingEmployee) {
                        
                        employee = existingOpt.get();
                    } else {
                        employee = new Employee();
                    employee.setCccd(cccd);
                    employee.setFullName(fullName);
                    employee.setDepartment(department);
                    employee.setPosition(position);
                    employee.setEmail(email);
                    employee.setPhone(phone);
                    employee.setOrganization(organization);
                    employee.setIsActive(true);
                    employee.setCreatedAt(LocalDateTime.now());

                    
                    if (imageBase64 != null && !imageBase64.isBlank()) {
                        employee.setImageRawUrl(imageBase64);
                        try {
                            double[] vector = faceAIService.extractVector(imageBase64);
                            if (vector != null) {
                                String vectorJson = objectMapper.writeValueAsString(vector);
                                employee.setFaceVector(vectorJson);
                                log.info("Excel import: Extracted face vector for CCCD={}", cccd);
                            } else {
                                log.warn("Excel import: Could not extract vector for CCCD={} (AI returned null)", cccd);
                            }
                        } catch (Exception ex) {
                            
                            log.warn("Excel import: Face AI error for CCCD={}: {}", cccd, ex.getMessage());
                        }
                    }

                    employee = employeeRepository.save(employee);
                    } 

                    
                    if (doorCodes != null && !doorCodes.isBlank()) {
                        
                        java.time.LocalTime parsedStartTime = null;
                        java.time.LocalTime parsedEndTime = null;
                        java.time.LocalDate parsedStartDate = null;
                        java.time.LocalDate parsedEndDate = null;
                        
                        final String fStartTimeStr = startTimeStr;
                        final String fEndTimeStr = endTimeStr;
                        final String fStartDateStr = startDateStr;
                        final String fEndDateStr = endDateStr;
                        try { if (fStartTimeStr != null && !fStartTimeStr.isEmpty()) parsedStartTime = fStartTimeStr.length() == 5 ? java.time.LocalTime.parse(fStartTimeStr + ":00") : java.time.LocalTime.parse(fStartTimeStr); } catch (Exception ignored) {}
                        try { if (fEndTimeStr != null && !fEndTimeStr.isEmpty()) parsedEndTime = fEndTimeStr.length() == 5 ? java.time.LocalTime.parse(fEndTimeStr + ":00") : java.time.LocalTime.parse(fEndTimeStr); } catch (Exception ignored) {}
                        try { if (fStartDateStr != null && !fStartDateStr.isEmpty()) parsedStartDate = java.time.LocalDate.parse(fStartDateStr); } catch (Exception ignored) {}
                        try { if (fEndDateStr != null && !fEndDateStr.isEmpty()) parsedEndDate = java.time.LocalDate.parse(fEndDateStr); } catch (Exception ignored) {}
                        final java.time.LocalTime fStartTime = parsedStartTime;
                        final java.time.LocalTime fEndTime = parsedEndTime;
                        final java.time.LocalDate fStartDate = parsedStartDate;
                        final java.time.LocalDate fEndDate = parsedEndDate;
                        final Employee finalEmployee = employee;
                        final String finalCccd = cccd;
                        Arrays.stream(doorCodes.split("[,;]"))
                            .map(String::trim)
                            .filter(code -> !code.isEmpty())
                            .forEach(deviceCode -> {
                                
                                deviceRepository.findByDeviceCodeIgnoreCase(deviceCode).ifPresentOrElse(device -> {
                                    boolean exists = !accessPermissionRepository
                                        .findByEmployeeIdAndDeviceId(finalEmployee.getId(), device.getId())
                                        .isEmpty();
                                    if (!exists) {
                                        AccessPermission perm = new AccessPermission();
                                        perm.setEmployee(finalEmployee);
                                        perm.setDevice(device);
                                        perm.setIsActive(true);
                                        perm.setStartTime(fStartTime);
                                        perm.setEndTime(fEndTime);
                                        perm.setStartDate(fStartDate);
                                        perm.setEndDate(fEndDate);
                                        accessPermissionRepository.save(perm);
                                        log.info("Excel import: Granted access for CCCD={} to device={}", finalCccd, device.getDeviceCode());
                                    } else {
                                        log.info("Excel import: Permission already exists for CCCD={} device={}", finalCccd, device.getDeviceCode());
                                    }
                                }, () -> log.warn("Excel import: Device not found for code='{}' (CCCD={})", deviceCode, finalCccd));
                            });
                    }

                    String status = isExistingEmployee ? " [cập nhật quyền]" : (employee.getFaceVector() != null ? " [vector OK]" : " [no vector]");
                    successList.add("CCCD: " + cccd + status + (doorCodes != null && !doorCodes.isBlank() ? " [cua: " + doorCodes + "]" : ""));
                } catch (Exception e) {
                    failureList.add("Row " + (i + 1) + ": " + e.getMessage());
                }
            }
        }

        
        result.put("totalRows", lastRowNum);
        result.put("successCount", successList.size());
        result.put("failureCount", failureList.size());
        result.put("successList", successList);
        result.put("failureList", failureList);

        
        successList.stream()
            .flatMap(s -> {
                int idx = s.indexOf("[cua: ");
                if (idx < 0) return java.util.stream.Stream.empty();
                String codes = s.substring(idx + 6, s.lastIndexOf("]"));
                return Arrays.stream(codes.split("[,;]")).map(String::trim);
            })
            .distinct()
            .filter(code -> !code.isBlank())
            .forEach(deviceCode -> {
                try { attendanceService.publishSyncData(deviceCode); }
                catch (Exception ex) { log.warn("SYNC_DATA failed for device {}: {}", deviceCode, ex.getMessage()); }
            });

        return result;
    }

    
    private String getCellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue()).trim();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    

    public List<Employee> getEmployeesByDevice(String deviceCode) {
        return accessPermissionRepository.findByDeviceId(
                deviceRepository.findByDeviceCode(deviceCode)
                        .orElseThrow(() -> new RuntimeException("Device not found: " + deviceCode))
                        .getId()
        ).stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .map(AccessPermission::getEmployee)
                .filter(e -> Boolean.TRUE.equals(e.getIsActive()))
                .collect(java.util.stream.Collectors.toList());
    }

    

    public Page<Employee> getEmployeesByOrganization(Long organizationId, Pageable pageable) {
        return employeeRepository.findAll(pageable);
    }
}
