package prj3.example.Prj3.controller;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import prj3.example.Prj3.entity.AttendanceLog;
import prj3.example.Prj3.entity.Employee;
import prj3.example.Prj3.entity.Organization;
import prj3.example.Prj3.repository.AttendanceLogRepository;
import prj3.example.Prj3.repository.EmployeeRepository;
import prj3.example.Prj3.repository.OrganizationRepository;
import prj3.example.Prj3.service.FaceAIService;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/face")
public class FaceController {
    private static final Logger logger = LoggerFactory.getLogger(FaceController.class);
    
    @Value("${face-ai.comparison.threshold.primary:0.55}")
    private double thresholdPrimary;
    
    @Value("${face-ai.comparison.threshold.backup:0.45}")
    private double thresholdBackup;
    
    @Autowired
    private FaceAIService faceAIService;
    
    @Autowired
    private AttendanceLogRepository attendanceLogRepository;
    
    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private OrganizationRepository organizationRepository;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    

    @PostMapping("/compare-v2")
    public ResponseEntity<?> compareV2(@RequestBody Map<String, Object> request) {
        try {
            String chipImage = (String) request.get("chipImage");
            String selfieImage = (String) request.get("selfieImage");
            String cccd = (String) request.get("cccd");
            Long organizationId = Long.parseLong(request.getOrDefault("organizationId", "1").toString());

            if (chipImage == null || selfieImage == null) {
                logger.warn("Missing required images in request");
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Thiếu ảnh chip hoặc ảnh selfie"
                ));
            }

            logger.info("=== Bắt đầu so sánh khuôn mặt (Mode 2) ===");
            logger.info("CCCD: {}, OrgID: {}", cccd, organizationId);

            return performHybridComparison(chipImage, selfieImage, cccd, organizationId);

        } catch (Exception e) {
            logger.error("Lỗi trong endpoint /compare-v2", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Lỗi server: " + e.getMessage()
            ));
        }
    }

    

    @PostMapping("/compare")
    public ResponseEntity<?> compare(@RequestParam("chip") MultipartFile chipFile,
                                     @RequestParam("selfie") MultipartFile selfieFile,
                                     @RequestParam(value = "cccd", required = false) String cccd,
                                     @RequestParam(value = "organizationId", defaultValue = "1") Long organizationId) {
        try {
            String chipImage = Base64.getEncoder().encodeToString(chipFile.getBytes());
            String selfieImage = Base64.getEncoder().encodeToString(selfieFile.getBytes());

            logger.info("=== Bắt đầu so sánh khuôn mặt (Mode 1 - Legacy) ===");
            logger.info("CCCD: {}, OrgID: {}", cccd, organizationId);

            return performHybridComparison(chipImage, selfieImage, cccd, organizationId);

        } catch (IOException e) {
            logger.error("Lỗi đọc file upload", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Lỗi đọc file hình ảnh"
            ));
        } catch (Exception e) {
            logger.error("Lỗi trong endpoint /compare", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Lỗi server: " + e.getMessage()
            ));
        }
    }

    

    private ResponseEntity<?> performHybridComparison(String chipImage, String selfieImage, 
                                                       String cccd, Long organizationId) throws Exception {
        
        logger.info(">>> MODE 1: So sánh trực tiếp (chip vs selfie)");
        
        Map<String, Object> primaryResult = faceAIService.compareTwoImages(
            chipImage, selfieImage, thresholdPrimary
        );
        
        double score = ((Number) primaryResult.get("score")).doubleValue();
        boolean matched = (boolean) primaryResult.get("matched");

        logger.info("Score: {}, Threshold primary: {}, Matched: {}", score, thresholdPrimary, matched);

        
        if (score >= thresholdPrimary) {
            logger.info("✓ Mode 1: Khuôn mặt khớp (Score: {})", score);
            return saveAttendanceAndRespond(true, score, cccd, organizationId, selfieImage, "MODE_1");
        }

        
        if (score < thresholdBackup) {
            logger.warn("✗ Mode 1: Khuôn mặt không khớp (Score: {})", score);
            AttendanceLog failedLog = createAttendanceLog(
                false, score, cccd, organizationId, selfieImage, "MODE_1_FAILED"
            );
            attendanceLogRepository.save(failedLog);
            
            return ResponseEntity.ok(Map.of(
                "success", false,
                "matched", false,
                "score", String.format("%.4f", score),
                "message", "Khuôn mặt không khớp (Score " + String.format("%.2f", score * 100) + "%)"
            ));
        }

        
        logger.warn("⚠ MODE 1: Gray zone (Score: {}) - Chuyển sang Mode 2 (Backup)", score);
        if (cccd == null || cccd.isEmpty()) {
            logger.warn("Không thể chuyển sang Mode 2: CCCD không được cung cấp");
            return respondWithScore(false, score, "Chất lượng ảnh thấp, cần cung cấp CCCD để xác thực");
        }

        
        logger.info(">>> MODE 2: So sánh vector backup từ DB (selfie)");
        
        Optional<Employee> employeeOpt = employeeRepository.findByCccdAndOrganizationId(cccd, organizationId);
        if (!employeeOpt.isPresent()) {
            logger.warn("Không tìm thấy nhân viên với CCCD: {} trong organizationId: {}", cccd, organizationId);
            return respondWithScore(false, score, "Không tìm thấy thông tin nhân viên trong hệ thống");
        }

        Employee employee = employeeOpt.get();
        String backupEmbeddingJson = employee.getBackupPhotoEmbedding();

        if (backupEmbeddingJson == null || backupEmbeddingJson.isEmpty()) {
            logger.warn("Nhân viên {} không có vector backup lưu trữ", employee.getId());
            return respondWithScore(false, score, "Không có dữ liệu backup để xác thực");
        }

        try {
            
            double[] backupEmbedding = objectMapper.readValue(backupEmbeddingJson, double[].class);

            
            Map<String, Object> fallbackResult = faceAIService.compareVectorWithImage(
                backupEmbedding, selfieImage, thresholdPrimary
            );

            double fallbackScore = ((Number) fallbackResult.get("score")).doubleValue();
            boolean fallbackMatched = (boolean) fallbackResult.get("matched");

            logger.info("Mode 2 Result - Score: {}, Matched: {}", fallbackScore, fallbackMatched);

            if (fallbackMatched) {
                logger.info("✓ Mode 2: Khuôn mặt khớp (Backup Score: {})", fallbackScore);
                return saveAttendanceAndRespond(true, fallbackScore, cccd, organizationId, selfieImage, "MODE_2_FALLBACK");
            } else {
                logger.warn("✗ Mode 2: Khuôn mặt không khớp (Backup Score: {})", fallbackScore);
                AttendanceLog failedLog = createAttendanceLog(
                    false, fallbackScore, cccd, organizationId, selfieImage, "MODE_2_FAILED"
                );
                attendanceLogRepository.save(failedLog);
                
                return respondWithScore(false, fallbackScore, 
                    "Xác thực thất bại - Vector không khớp (Score " + String.format("%.2f", fallbackScore * 100) + "%)");
            }

        } catch (Exception e) {
            logger.error("Lỗi xử lý Mode 2", e);
            return respondWithScore(false, score, "Lỗi so sánh vector backup: " + e.getMessage());
        }
    }

    

    private ResponseEntity<?> saveAttendanceAndRespond(boolean matched, double score, String cccd, 
                                                        Long organizationId, String selfieImage, String mode) throws Exception {
        AttendanceLog log = createAttendanceLog(matched, score, cccd, organizationId, selfieImage, mode);
        AttendanceLog savedLog = attendanceLogRepository.save(log);

        logger.info("✓ Đã lưu attendance log ID: {}", savedLog.getId());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "matched", true,
            "score", String.format("%.4f", score),
            "logId", savedLog.getId(),
            "mode", mode,
            "message", "Xác thực thành công - " + log.getEmployeeName()
        ));
    }

    

    private AttendanceLog createAttendanceLog(boolean matched, double score, String cccd, 
                                              Long organizationId, String selfieImage, String mode) throws Exception {
        AttendanceLog log = new AttendanceLog();
        log.setCccd(cccd);
        log.setScore(score);
        log.setMatchScore(score);
        log.setMatched(matched);
        log.setAccessGranted(matched);
        log.setComparisonMode(mode);
        log.setComparisonMethod(mode);
        log.setScanTime(LocalDateTime.now());
        log.setSelfieImage(selfieImage);
        log.setStatus(matched ? "SUCCESS" : "FAILED");

        Organization org = organizationRepository.findById(organizationId).orElse(null);
        log.setOrganization(org);

        if (cccd != null && !cccd.isEmpty() && org != null) {
            Optional<Employee> empOpt = employeeRepository.findByCccdAndOrganizationId(cccd, organizationId);
            if (empOpt.isPresent()) {
                Employee emp = empOpt.get();
                log.setEmployee(emp);
                log.setFullName(emp.getFullName());
                log.setEmployeeName(emp.getFullName());
                log.setCapturedName(emp.getFullName());
                log.setDeviceCode(emp.getDepartment());
                logger.info("Xác định danh tính: {}", emp.getFullName());
            } else {
                log.setDeviceCode("UNKNOWN");
                log.setEmployeeName("Khách vãng lai");
            }
        }

        return log;
    }

    

    private ResponseEntity<?> respondWithScore(boolean matched, double score, String message) {
        return ResponseEntity.ok(Map.of(
            "success", matched,
            "matched", matched,
            "score", String.format("%.4f", score),
            "message", message
        ));
    }

    

    @GetMapping("/employee/{cccd}")
    public ResponseEntity<?> getEmployeeForFaceCompare(@PathVariable String cccd) {
        try {
            Optional<Employee> empOpt = employeeRepository.findByCccd(cccd);

            
            if (empOpt.isEmpty() && cccd.length() <= 9) {
                java.util.List<Employee> candidates = employeeRepository.findByCccdEndingWith(cccd);
                if (candidates.size() == 1) empOpt = Optional.of(candidates.get(0));
            }

            if (empOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of(
                    "found", false,
                    "message", "Employee not found for CCCD: " + cccd
                ));
            }

            Employee emp = empOpt.get();
            return ResponseEntity.ok(Map.of(
                "found", true,
                "cccd", emp.getCccd(),
                "fullName", emp.getFullName() != null ? emp.getFullName() : "",
                "backupPhoto", emp.getImageRawUrl() != null ? emp.getImageRawUrl() : "",
                "faceVector", emp.getFaceVector() != null ? emp.getFaceVector() : "",
                "backupPhotoEmbedding", emp.getBackupPhotoEmbedding() != null ? emp.getBackupPhotoEmbedding() : ""
            ));
        } catch (Exception e) {
            logger.error("Error fetching employee for face compare: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("found", false, "message", e.getMessage()));
        }
    }
}