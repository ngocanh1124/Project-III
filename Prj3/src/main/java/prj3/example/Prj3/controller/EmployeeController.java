package prj3.example.Prj3.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import prj3.example.Prj3.dto.FirstRegistrationRequestDTO;
import prj3.example.Prj3.entity.Employee;
import prj3.example.Prj3.enums.ActivationStatus;
import prj3.example.Prj3.repository.EmployeeRepository;
import prj3.example.Prj3.service.ExcelService;
import prj3.example.Prj3.service.FirstRegistrationService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    @Autowired private ExcelService excelService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private FirstRegistrationService firstRegistrationService;

    
    

    @PostMapping("/import")
    public ResponseEntity<?> importEmployees(@RequestParam("file") MultipartFile file) {
        try {
            excelService.importEmployees(file);
            return ResponseEntity.ok(Map.of(
                "message", "Import thành công. Nhân viên đang ở trạng thái PENDING_ACTIVATION.",
                "note", "Nhân viên cần đến trạm để thực hiện đăng ký sinh trắc học lần đầu."
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Lỗi khi xử lý file Excel: " + e.getMessage())
            );
        }
    }

    
    @GetMapping("/list")
    public ResponseEntity<List<Employee>> getEmployeeList() {
        return ResponseEntity.ok(employeeRepository.findAll());
    }

    
    

    @GetMapping("/pending")
    public ResponseEntity<List<Employee>> getPendingEmployees() {
        return ResponseEntity.ok(
            employeeRepository.findByActivationStatus(ActivationStatus.PENDING_ACTIVATION)
        );
    }

    
    @GetMapping("/active")
    public ResponseEntity<List<Employee>> getActiveEmployees() {
        return ResponseEntity.ok(
            employeeRepository.findByActivationStatus(ActivationStatus.ACTIVE)
        );
    }

    
    

    @PostMapping("/{id}/first-registration")
    public ResponseEntity<?> firstRegistration(
            @PathVariable Long id,
            @RequestBody FirstRegistrationRequestDTO req) {
        req.setEmployeeId(id);
        Map<String, Object> result = firstRegistrationService.registerFirstTime(req);
        boolean success = Boolean.TRUE.equals(result.get("success"));
        return success ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    

    @PostMapping("/first-registration/by-cccd")
    public ResponseEntity<?> firstRegistrationByCccd(@RequestBody FirstRegistrationRequestDTO req) {
        Map<String, Object> result = firstRegistrationService.registerFirstTime(req);
        boolean success = Boolean.TRUE.equals(result.get("success"));
        return success ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    
    @GetMapping("/{id}")
    public ResponseEntity<?> getEmployee(@PathVariable Long id) {
        Optional<Employee> emp = employeeRepository.findById(id);
        return emp.map(ResponseEntity::ok)
                  .orElseGet(() -> ResponseEntity.notFound().build());
    }

    
    

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<?> deactivate(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        String reason = body != null ? (String) body.getOrDefault("reason", "Admin") : "Admin";
        boolean eraseBiometrics = body != null && Boolean.TRUE.equals(body.get("eraseBiometrics"));
        Map<String, Object> result = firstRegistrationService.deactivateEmployee(id, reason, eraseBiometrics);
        boolean success = Boolean.TRUE.equals(result.get("success"));
        return success ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    
    

    @PatchMapping("/{id}/reactivate")
    public ResponseEntity<?> reactivate(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        String reason = body != null ? (String) body.getOrDefault("reason", "Admin") : "Admin";
        Map<String, Object> result = firstRegistrationService.reactivateEmployee(id, reason);
        boolean success = Boolean.TRUE.equals(result.get("success"));
        return success ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }
}
