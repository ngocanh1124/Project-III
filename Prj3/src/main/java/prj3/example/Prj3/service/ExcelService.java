package prj3.example.Prj3.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import prj3.example.Prj3.entity.AccessPermission;
import prj3.example.Prj3.entity.Device;
import prj3.example.Prj3.entity.Employee;
import prj3.example.Prj3.enums.ActivationStatus;
import prj3.example.Prj3.enums.PersonType;
import prj3.example.Prj3.repository.AccessPermissionRepository;
import prj3.example.Prj3.repository.DeviceRepository;
import prj3.example.Prj3.repository.EmployeeRepository;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@Slf4j
@Service
public class ExcelService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("H:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DeviceRepository deviceRepository;
    @Autowired private AccessPermissionRepository permissionRepository;

    public void importEmployees(MultipartFile file) throws Exception {
        Workbook workbook = new XSSFWorkbook(file.getInputStream());
        Sheet sheet = workbook.getSheetAt(0);
        int imported = 0, skipped = 0;

        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue; 

            String cccd = cellString(row, 0);
            String fullName = cellString(row, 1);
            if (cccd == null || cccd.isBlank() || fullName == null || fullName.isBlank()) {
                skipped++;
                continue;
            }

            
            Employee emp = employeeRepository.findByCccd(cccd).orElse(new Employee());
            boolean isNew = emp.getId() == null;

            emp.setCccd(cccd);
            emp.setFullName(fullName);

            String empCode = cellString(row, 2);
            if (empCode != null && !empCode.isBlank()) emp.setEmployeeCode(empCode);
            String dept = cellString(row, 3);
            if (dept != null && !dept.isBlank()) emp.setDepartment(dept);
            String pos = cellString(row, 4);
            if (pos != null && !pos.isBlank()) emp.setPosition(pos);
            String email = cellString(row, 5);
            if (email != null && !email.isBlank()) emp.setEmail(email);
            String phone = cellString(row, 6);
            if (phone != null && !phone.isBlank()) emp.setPhone(phone);

            
            String personTypeStr = cellString(row, 12);
            PersonType personType = PersonType.EMPLOYEE;
            if ("CANDIDATE".equalsIgnoreCase(personTypeStr)) personType = PersonType.CANDIDATE;
            else if ("VISITOR".equalsIgnoreCase(personTypeStr)) personType = PersonType.VISITOR;
            emp.setPersonType(personType);

            
            if (isNew) {
                emp.setActivationStatus(ActivationStatus.PENDING_ACTIVATION);
                emp.setIsActive(true);
                log.info("Import new {} (PENDING): cccd={}, name={}", personType, cccd, fullName);
            }

            Employee saved = employeeRepository.save(emp);

            
            String deviceCode = cellString(row, 7);
            if (deviceCode != null && !deviceCode.isBlank()) {
                Optional<Device> deviceOpt = deviceRepository.findByDeviceCode(deviceCode.trim());
                if (deviceOpt.isPresent()) {
                    Device device = deviceOpt.get();

                    
                    boolean alreadyExists = permissionRepository
                            .existsByEmployeeIdAndDeviceId(saved.getId(), device.getId());
                    if (!alreadyExists) {
                        AccessPermission perm = new AccessPermission();
                        perm.setEmployee(saved);
                        perm.setDevice(device);
                        perm.setStartTime(parseTime(cellString(row, 8)));
                        perm.setEndTime(parseTime(cellString(row, 9)));
                        perm.setStartDate(parseDate(cellString(row, 10)));
                        perm.setEndDate(parseDate(cellString(row, 11)));
                        
                        perm.setIsActive(false);
                        permissionRepository.save(perm);
                        log.info("Created INACTIVE permission: cccd={}, device={}", cccd, deviceCode);
                    }
                } else {
                    log.warn("Device not found, skipping permission: deviceCode={}", deviceCode);
                }
            }
            imported++;
        }
        workbook.close();
        log.info("Excel import done — imported={}, skipped={}", imported, skipped);
    }

    

    private String cellString(Row row, int idx) {
        Cell cell = row.getCell(idx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        cell.setCellType(CellType.STRING);
        String v = cell.getStringCellValue();
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private LocalTime parseTime(String s) {
        if (s == null) return null;
        try { return LocalTime.parse(s, TIME_FMT); } catch (DateTimeParseException e) { return null; }
    }

    private LocalDate parseDate(String s) {
        if (s == null) return null;
        try { return LocalDate.parse(s, DATE_FMT); } catch (DateTimeParseException e) { return null; }
    }
}
