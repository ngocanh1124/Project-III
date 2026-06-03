package prj3.example.Prj3.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import prj3.example.Prj3.entity.AttendanceLog;
import prj3.example.Prj3.service.AttendanceService;
import prj3.example.Prj3.service.FaceComparisonService;
import prj3.example.Prj3.repository.AttendanceLogRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    @Autowired 
    private AttendanceService attendanceService;

    @Autowired 
    private FaceComparisonService faceComparisonService;

    @Autowired 
    private AttendanceLogRepository logRepository;

    
    @PostMapping("/record")
    public ResponseEntity<?> recordAttendance(@RequestBody AttendanceLog log) {
        
        AttendanceLog savedLog = attendanceService.processAttendanceAndReply(log);
        
        return ResponseEntity.ok(Map.of(
            "success", true, 
            "message", "Đã ghi nhận lịch sử",
            "id", savedLog.getId()
        ));
    }

    
    @GetMapping("/history")
    public ResponseEntity<List<AttendanceLog>> getHistory() {
        
        return ResponseEntity.ok(logRepository.findAllByOrderByScanTimeDesc());
    }

    
    @PostMapping("/remote-open/{deviceCode}")
    public ResponseEntity<?> remoteOpen(@PathVariable String deviceCode) {
        attendanceService.remoteOpenDoor(deviceCode);
        return ResponseEntity.ok(Map.of("message", "Đã gửi lệnh mở cửa tới " + deviceCode));
    }

    

    

    @PostMapping("/face/compare")
    public ResponseEntity<?> compareFaces(@RequestBody Map<String, String> request) {
        try {
            String face1Base64 = request.get("face1_base64");
            String face2Base64 = request.get("face2_base64");
            
            if (face1Base64 == null || face2Base64 == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "face1_base64 and face2_base64 are required"
                ));
            }
            
            log.info("Face comparison requested");
            FaceComparisonService.FaceComparisonResult result = 
                faceComparisonService.compareFaces(face1Base64, face2Base64);
            
            return ResponseEntity.ok(Map.of(
                "success", result.success,
                "similarity", result.similarity,
                "matched", result.matched,
                "confidence", result.confidence,
                "message", result.success ? "Comparison completed" : result.message
            ));
            
        } catch (Exception e) {
            log.error("Error comparing faces", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", "Face comparison failed: " + e.getMessage()
            ));
        }
    }

    

    @PostMapping("/face/extract")
    public ResponseEntity<?> extractFaceVector(@RequestBody Map<String, String> request) {
        try {
            String imageBase64 = request.get("image_base64");
            String cccd = request.get("cccd");
            
            if (imageBase64 == null || cccd == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "image_base64 and cccd are required"
                ));
            }
            
            log.info("Face vector extraction requested for {}", cccd);
            FaceComparisonService.FaceVectorResult result = 
                faceComparisonService.extractFaceVector(imageBase64, cccd);
            
            return ResponseEntity.ok(Map.of(
                "success", result.success,
                "vector_base64", result.success ? result.vectorBase64 : null,
                "quality", result.quality,
                "message", result.success ? "Vector extracted successfully" : result.message
            ));
            
        } catch (Exception e) {
            log.error("Error extracting face vector", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", "Vector extraction failed: " + e.getMessage()
            ));
        }
    }

    

    @GetMapping("/face/health")
    public ResponseEntity<?> checkFaceServiceHealth() {
        boolean healthy = faceComparisonService.checkHealth();
        return ResponseEntity.ok(Map.of(
            "face_service_healthy", healthy,
            "message", healthy ? "Face AI service is ready" : "Face AI service is down"
        ));
    }
}