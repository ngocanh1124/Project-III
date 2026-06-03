package prj3.example.Prj3.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import prj3.example.Prj3.service.ExcelService;
import prj3.example.Prj3.service.AttendanceService;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    @Autowired private ExcelService excelService;
    @Autowired private AttendanceService attendanceService;

    @PostMapping("/import")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('HR_MANAGER','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> importData(@RequestParam("file") MultipartFile file) {
        try {
            excelService.importEmployees(file);
            return ResponseEntity.ok("Thành công");
        } catch (Exception e) { return ResponseEntity.internalServerError().body(e.getMessage()); }
    }

    @PostMapping("/open/{deviceCode}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('OPERATOR','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> remoteOpen(@PathVariable String deviceCode) {
        attendanceService.remoteOpenDoor(deviceCode);
        return ResponseEntity.ok("Sent");
    }
}