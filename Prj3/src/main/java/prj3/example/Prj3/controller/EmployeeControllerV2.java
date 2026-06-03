package prj3.example.Prj3.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import prj3.example.Prj3.dto.*;
import prj3.example.Prj3.entity.Employee;
import prj3.example.Prj3.entity.Organization;
import prj3.example.Prj3.service.EmployeeService;
import prj3.example.Prj3.repository.EmployeeRepository;
import prj3.example.Prj3.repository.OrganizationRepository;
import org.springframework.security.access.prepost.PreAuthorize;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v2/employees")
public class EmployeeControllerV2 {
    
    private static final Logger logger = LoggerFactory.getLogger(EmployeeControllerV2.class);

    @Autowired
    private EmployeeService employeeService;
    
    @Autowired
    private EmployeeRepository employeeRepository;
    
    @Autowired
    private OrganizationRepository organizationRepository;

    

    @GetMapping
    public ResponseEntity<APIResponseDTO<Page<EmployeeDTO>>> getEmployees(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {
        
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Employee> employees;
            
            if (search != null && !search.isEmpty()) {
                employees = employeeRepository.findByCccdOrFullNameContaining(search, search, pageable);
            } else if (status != null) {
                boolean isActive = "active".equalsIgnoreCase(status);
                employees = employeeRepository.findByIsActive(isActive, pageable);
            } else {
                employees = employeeRepository.findAll(pageable);
            }
            
            Page<EmployeeDTO> dtos = employees.map(this::convertToDTO);
            
            return ResponseEntity.ok(
                    APIResponseDTO.ok(dtos, "Employees fetched successfully")
            );
        } catch (Exception e) {
            logger.error("Error fetching employees", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponseDTO.error("FETCH_ERROR", "Failed to fetch employees", e.getMessage()));
        }
    }

    

    @GetMapping("/{cccd}")
    public ResponseEntity<APIResponseDTO<EmployeeDTO>> getEmployee(@PathVariable String cccd) {
        try {
            Employee employee = employeeRepository.findByCccd(cccd)
                    .orElseThrow(() -> new Exception("Employee not found"));
            
            return ResponseEntity.ok(
                    APIResponseDTO.ok(convertToDTO(employee), "Employee retrieved")
            );
        } catch (Exception e) {
            logger.error("Error fetching employee", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(APIResponseDTO.error("NOT_FOUND", "Employee not found", e.getMessage()));
        }
    }

    

    @PostMapping
    @PreAuthorize("hasAnyRole('HR_MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<APIResponseDTO<EmployeeDTO>> createEmployee(
            @Valid @RequestBody EmployeeCreateDTO request,
            BindingResult validationResult) {
        
        if (validationResult.hasErrors()) {
            String errors = validationResult.getAllErrors().stream()
                    .map(e -> e.getDefaultMessage())
                    .collect(Collectors.joining(", "));
            return ResponseEntity.badRequest()
                    .body(APIResponseDTO.error("VALIDATION_ERROR", "Invalid request", errors));
        }
        
        try {
            Employee employee = employeeService.createEmployee(request);
            
            logger.info("Employee created - CCCD: {}", employee.getCccd());
            
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(APIResponseDTO.ok(convertToDTO(employee), "Employee created successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(APIResponseDTO.error("DUPLICATE_CCCD", e.getMessage(), null));
        } catch (Exception e) {
            logger.error("Error creating employee", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponseDTO.error("CREATE_ERROR", "Failed to create employee", e.getMessage()));
        }
    }

    

    @PutMapping("/{cccd}")
    @PreAuthorize("hasAnyRole('HR_MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<APIResponseDTO<EmployeeDTO>> updateEmployee(
            @PathVariable String cccd,
            @Valid @RequestBody EmployeeUpdateDTO request) {
        
        try {
            Employee employee = employeeService.updateEmployee(cccd, request);
            
            logger.info("Employee updated - CCCD: {}", cccd);
            
            return ResponseEntity.ok(
                    APIResponseDTO.ok(convertToDTO(employee), "Employee updated successfully")
            );
        } catch (Exception e) {
            logger.error("Error updating employee", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponseDTO.error("UPDATE_ERROR", "Failed to update employee", e.getMessage()));
        }
    }

    

    @DeleteMapping("/{cccd}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<APIResponseDTO<Void>> deleteEmployee(@PathVariable String cccd) {
        try {
            employeeService.deleteEmployee(cccd);
            
            logger.info("Employee deleted - CCCD: {}", cccd);
            
            return ResponseEntity.ok(
                    APIResponseDTO.ok(null, "Employee deleted successfully")
            );
        } catch (Exception e) {
            logger.error("Error deleting employee", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponseDTO.error("DELETE_ERROR", "Failed to delete employee", e.getMessage()));
        }
    }

    

    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('HR_MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<APIResponseDTO<Map<String, Object>>> importEmployees(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long organizationId) {
        
        logger.info("Importing employees from Excel - Organization: {}", organizationId);
        
        try {
            Map<String, Object> result = employeeService.importFromExcel(file, organizationId);
            
            return ResponseEntity.ok(
                    APIResponseDTO.ok(result, "Employees imported successfully")
            );
        } catch (Exception e) {
            logger.error("Error importing employees", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponseDTO.error("IMPORT_ERROR", "Failed to import employees", e.getMessage()));
        }
    }

    

    @GetMapping("/export")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportEmployees(
            @RequestParam(required = false) String status) {
        
        logger.info("Exporting employees to Excel");
        
        try {
            List<Employee> employees;
            
            if (status != null && "active".equalsIgnoreCase(status)) {
                employees = employeeRepository.findByIsActive(true);
            } else {
                employees = employeeRepository.findAll();
            }
            
            byte[] excelBytes = generateExcel(employees);
            
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=employees.xlsx")
                    .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .body(excelBytes);
        } catch (Exception e) {
            logger.error("Error exporting employees", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    

    @GetMapping("/device/{deviceCode}")
    public ResponseEntity<APIResponseDTO<List<EmployeeDTO>>> getEmployeesByDevice(
            @PathVariable String deviceCode) {
        
        try {
            List<Employee> employees = employeeService.getEmployeesByDevice(deviceCode);
            List<EmployeeDTO> dtos = employees.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(
                    APIResponseDTO.ok(dtos, "Employees for device fetched")
            );
        } catch (Exception e) {
            logger.error("Error fetching employees for device", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponseDTO.error("FETCH_ERROR", "Failed to fetch employees", e.getMessage()));
        }
    }

    
    private EmployeeDTO convertToDTO(Employee employee) {
        return EmployeeDTO.builder()
                .id(employee.getId())
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

    

    @PatchMapping("/{cccd}/status")
    public ResponseEntity<APIResponseDTO<EmployeeDTO>> updateStatus(
            @PathVariable String cccd,
            @RequestBody Map<String, Boolean> request) {
        try {
            Employee employee = employeeRepository.findByCccd(cccd)
                    .orElseThrow(() -> new RuntimeException("Employee not found"));
            employee.setIsActive(request.getOrDefault("isActive", true));
            employeeRepository.save(employee);
            return ResponseEntity.ok(APIResponseDTO.ok(convertToDTO(employee), "Status updated"));
        } catch (Exception e) {
            logger.error("Error updating employee status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponseDTO.error("UPDATE_ERROR", "Failed to update status", e.getMessage()));
        }
    }

    

    @PutMapping("/{cccd}/backup-photo")
    public ResponseEntity<APIResponseDTO<EmployeeDTO>> updateBackupPhoto(
            @PathVariable String cccd,
            @RequestBody Map<String, String> request) {
        try {
            Employee employee = employeeRepository.findByCccd(cccd)
                    .orElseThrow(() -> new RuntimeException("Employee not found"));
            employee.setImageRawUrl(request.get("imageBase64"));
            employeeRepository.save(employee);
            return ResponseEntity.ok(APIResponseDTO.ok(convertToDTO(employee), "Backup photo updated"));
        } catch (Exception e) {
            logger.error("Error updating backup photo", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(APIResponseDTO.error("UPDATE_ERROR", "Failed to update backup photo", e.getMessage()));
        }
    }

    private byte[] generateExcel(List<Employee> employees) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Employees");
        
        
        Row headerRow = sheet.createRow(0);
        String[] columns = {"CCCD", "Full Name", "Department", "Position", "Email", "Phone", "Status", "Created At"};
        
        for (int i = 0; i < columns.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns[i]);
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            cell.setCellStyle(style);
        }
        
        
        int rowNum = 1;
        for (Employee emp : employees) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(emp.getCccd());
            row.createCell(1).setCellValue(emp.getFullName());
            row.createCell(2).setCellValue(emp.getDepartment() != null ? emp.getDepartment() : "");
            row.createCell(3).setCellValue(emp.getPosition() != null ? emp.getPosition() : "");
            row.createCell(4).setCellValue(emp.getEmail() != null ? emp.getEmail() : "");
            row.createCell(5).setCellValue(emp.getPhone() != null ? emp.getPhone() : "");
            row.createCell(6).setCellValue(emp.getIsActive() ? "Active" : "Inactive");
            row.createCell(7).setCellValue(emp.getCreatedAt() != null ? emp.getCreatedAt().toString() : "");
        }
        
        
        for (int i = 0; i < columns.length; i++) {
            sheet.autoSizeColumn(i);
        }
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        workbook.close();
        
        return baos.toByteArray();
    }
}
