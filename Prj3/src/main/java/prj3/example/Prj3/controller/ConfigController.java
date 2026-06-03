package prj3.example.Prj3.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import prj3.example.Prj3.service.ConfigService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    @Autowired 
    private ConfigService configService;

    
    @GetMapping("/master-pin")
    public ResponseEntity<Map<String, String>> getMasterPin() {
        String pin = configService.getMasterPin();
        
        Map<String, String> response = new HashMap<>();
        response.put("pin", pin); 
        
        return ResponseEntity.ok(response);
    }

    
    @PostMapping("/update-pin")
    public ResponseEntity<?> updatePin(@RequestBody Map<String, String> request) {
        String newPin = request.get("newPin");
        if (newPin == null || newPin.isEmpty()) {
            return ResponseEntity.badRequest().body("Mã PIN không được để trống");
        }
        configService.updateMasterPin(newPin);
        return ResponseEntity.ok("Cập nhật mã PIN thành công!");
    }

    

    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        Map<String, String> info = new HashMap<>();
        info.put("app", "AttendanceSystem");
        info.put("version", "2.0");
        info.put("status", "ok");
        return ResponseEntity.ok(info);
    }
}