package prj3.example.Prj3.controller;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import prj3.example.Prj3.model.AttendanceLog;
import prj3.example.Prj3.model.Employee;
import prj3.example.Prj3.model.Organization;
import prj3.example.Prj3.repository.AttendanceLogRepository;
import prj3.example.Prj3.repository.EmployeeRepository;
import prj3.example.Prj3.repository.OrganizationRepository;
import prj3.example.Prj3.service.FaceSDKService;

@RestController
@RequestMapping("/api/face")
@CrossOrigin("*")
public class FaceController {
    private static final Logger logger = LoggerFactory.getLogger(FaceController.class);
    
    @Autowired
    private FaceSDKService faceSDKService;
    
    @Autowired
    private AttendanceLogRepository attendanceRepo;
    
    @Autowired
    private EmployeeRepository employeeRepo;

    @Autowired
    private OrganizationRepository orgRepo;

    @PostMapping("/compare")
    public ResponseEntity<?> compare(@RequestParam("chip") MultipartFile chip,
                                     @RequestParam("selfie") MultipartFile selfie,
                                     @RequestParam(value="cccd", required=false) String cccd,
                                     @RequestParam(value="organizationId", defaultValue="1") Long organizationId) {
        try {
            logger.info("Nhận request so sánh. CCCD: " + cccd + " | OrgID: " + organizationId);
            
            Map<String, Object> result = faceSDKService.compareFaces(chip, selfie);
            double score = 0.0;
            if (result.get("score") != null) {
                score = Double.parseDouble(result.get("score").toString());
            }
            
            boolean matched = score >= 0.6; 
            
            if (matched) {
                AttendanceLog log = new AttendanceLog();
                log.setCccd(cccd);
                log.setScore(score);
                log.setMatched(true);
                log.setTimestamp(LocalDateTime.now());

                Organization org = orgRepo.findById(organizationId).orElse(null);
                log.setOrg(org);

                if (cccd != null && !cccd.isEmpty() && org != null) {
                    Optional<Employee> empOpt = employeeRepo.findByCccdAndOrgId(cccd, organizationId);
                    
                    if (empOpt.isPresent()) {
                        log.setEmployee(empOpt.get()); 
                        logger.info("Đã xác định danh tính: " + empOpt.get().getFullName());
                    } else {
                        log.setEmployee(null);
                        log.setCapturedName("Khách vãng lai");
                        logger.info("CCCD " + cccd + " chưa có trong danh sách nhân viên công ty này.");
                    }
                }
                
                attendanceRepo.save(log);
                logger.info("Đã lưu kết quả thành công.");
            } else {
                logger.warn("Không khớp (Score: " + score + ") - Bỏ qua, không lưu.");
            }
            
            return ResponseEntity.ok(Map.of(
                "matched", matched, 
                "score", score,
                "message", matched ? "Xác thực thành công" : "Khuôn mặt không khớp"
            ));
            
        } catch(Exception e){
            logger.error("Lỗi Server:", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}