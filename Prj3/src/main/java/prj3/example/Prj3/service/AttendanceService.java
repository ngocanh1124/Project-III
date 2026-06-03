package prj3.example.Prj3.service;

import prj3.example.Prj3.dto.AttendanceRecordRequestDTO;
import prj3.example.Prj3.dto.AttendanceRecordResponseDTO;
import prj3.example.Prj3.entity.AccessPermission;
import prj3.example.Prj3.entity.AttendanceLog;
import prj3.example.Prj3.entity.Employee;
import prj3.example.Prj3.enums.ActivationStatus;
import prj3.example.Prj3.repository.AccessPermissionRepository;
import prj3.example.Prj3.repository.AttendanceLogRepository;
import prj3.example.Prj3.repository.EmployeeRepository;
import prj3.example.Prj3.websocket.AttendanceWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class AttendanceService {

    
    private static final String MQTT_TOPIC_PREFIX = "cccd/devices";

    @Autowired private AttendanceLogRepository logRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private AccessPermissionRepository permissionRepository;
    @Autowired private MqttGateway mqttGateway;
    @Autowired private AttendanceWebSocketHandler webSocketHandler;
    @Autowired private FaceComparisonService faceComparisonService;

    @Value("${face-ai.comparison.threshold.primary:0.55}")
    private double faceThreshold;

    @Value("${face-ai.comparison.threshold.backup:0.45}")
    private double backupThreshold;

    public AttendanceLog processAttendanceAndReply(AttendanceLog record) {
        AttendanceLog savedLog = logRepository.save(record);

        try {
            webSocketHandler.broadcastAttendance(savedLog);
        } catch (Exception wsEx) {
            log.warn("WebSocket broadcast failed (record already saved, id={}): {}", savedLog.getId(), wsEx.getMessage());
        }

        if (Boolean.TRUE.equals(record.getMatched()) || "SUCCESS".equals(record.getStatus())) {
            try {
                String topic = MQTT_TOPIC_PREFIX + "/" + record.getDeviceCode() + "/command";
                int openMs = 3000;
                
                String message = "{\"action\":\"OPEN_DOOR\"," +
                        "\"duration_ms\":" + openMs + "," +
                        "\"name\":\"" + record.getCapturedName() + "\"," +
                        "\"relay\":true}";
                mqttGateway.sendToMqtt(message, topic);
                log.info("Sent OPEN_DOOR to MQTT topic: {}", topic);
                
                scheduleDoorClose(topic, openMs + 500);
            } catch (Exception mqttEx) {
                log.warn("MQTT OPEN_DOOR failed (record saved, id={}): {}", savedLog.getId(), mqttEx.getMessage());
            }
        }

        log.info("Attendance saved: id={}, cccd={}, device={}, status={}, score={}",
                savedLog.getId(), savedLog.getCccd(), savedLog.getDeviceCode(),
                savedLog.getStatus(), savedLog.getScore());
        return savedLog;
    }

    public Map<String, Object> remoteUnlockDoor(String deviceCode, String reason) {
        log.info("Remote unlock requested - Device: {}, Reason: {}", deviceCode, reason);
        String topic = MQTT_TOPIC_PREFIX + "/" + deviceCode + "/command";
        String safeReason = (reason != null ? reason : "Mở cửa từ xa").replace("\"", "").replace("\\", "");
        
        
        String message = "{\"action\":\"START_AUTH_FLOW\"," +
                "\"reason\":\"" + safeReason + "\"}";
        mqttGateway.sendToMqtt(message, topic);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("deviceCode", deviceCode);
        result.put("reason", reason);
        result.put("topic", topic);
        result.put("timestamp", LocalDateTime.now());
        return result;
    }

    public void remoteOpenDoor(String deviceCode) {
        remoteUnlockDoor(deviceCode, "Admin manual");
    }

    

    public Map<String, Object> instantUnlockDoor(String deviceCode, String reason) {
        log.info("Instant unlock - Device: {}, Reason: {}", deviceCode, reason);
        String topic = MQTT_TOPIC_PREFIX + "/" + deviceCode + "/command";
        String safeReason = (reason != null ? reason : "Admin mở cửa trực tiếp").replace("\"", "").replace("\\", "");
        int openMs = 5000;
        String message = "{\"action\":\"OPEN_DOOR\"," +
                "\"duration_ms\":" + openMs + "," +
                "\"name\":\"Admin\"," +
                "\"reason\":\"" + safeReason + "\"," +
                "\"relay\":true}";
        mqttGateway.sendToMqtt(message, topic);
        scheduleDoorClose(topic, openMs + 500);
        log.info("INSTANT_UNLOCK sent OPEN_DOOR to device: {}", deviceCode);

        
        try {
            AttendanceLog record = new AttendanceLog();
            record.setDeviceCode(deviceCode);
            record.setCccd("");
            record.setFullName("Admin mở cửa trực tiếp");
            record.setCapturedName("Admin mở cửa trực tiếp");
            record.setComparisonMode("REMOTE_UNLOCK");
            record.setComparisonMethod("INSTANT_UNLOCK");
            record.setStatus("SUCCESS");
            record.setAccessGranted(true);
            record.setMatched(true);
            record.setScore(1.0);
            record.setMatchScore(1.0);
            record.setScanTime(LocalDateTime.now());
            AttendanceLog saved = logRepository.save(record);
            webSocketHandler.broadcastAttendance(saved);
        } catch (Exception e) {
            log.warn("INSTANT_UNLOCK log save failed (door already opened): {}", e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("deviceCode", deviceCode);
        result.put("reason", reason);
        result.put("mode", "INSTANT");
        result.put("timestamp", LocalDateTime.now());
        return result;
    }

    

    public Map<String, Object> logRemoteEntry(String deviceCode, String visitorName,
                                               String selfieImage, String chipImage, String cccdNumber) {
        log.info("Remote entry - Device: {}, Visitor: {}, CCCD: {}", deviceCode, visitorName, cccdNumber);

        
        FaceComparisonService.FaceComparisonResult faceResult =
                faceComparisonService.compareFaces(selfieImage, chipImage);

        if (!faceResult.success || !faceResult.matched) {
            double score = faceResult.similarity;
            log.warn("Remote entry REJECTED - face mismatch for visitor: {}, score: {}", visitorName, score);
            Map<String, Object> failResult = new HashMap<>();
            failResult.put("success", false);
            failResult.put("matched", false);
            failResult.put("score", score);
            failResult.put("message",
                    String.format("Khuôn mặt không khớp với ảnh chip CCCD (%.1f%%). Vui lòng thử lại.", score * 100));
            return failResult;
        }

        log.info("Remote entry face MATCHED - score: {}", faceResult.similarity);
        String displayName = (visitorName != null && !visitorName.isBlank()) ? visitorName : "Khách vãng lai";

        
        AttendanceLog record = new AttendanceLog();
        record.setDeviceCode(deviceCode);
        record.setCccd(cccdNumber != null ? cccdNumber : "");
        record.setFullName(displayName);
        record.setEmployeeName(displayName);
        record.setCapturedName(displayName);
        record.setComparisonMode("REMOTE_UNLOCK");
        record.setComparisonMethod("REMOTE_UNLOCK_FACE_VERIFY");
        record.setStatus("SUCCESS");
        record.setAccessGranted(true);
        record.setMatched(true);
        record.setScore(faceResult.similarity);
        record.setMatchScore(faceResult.similarity);
        record.setScanTime(LocalDateTime.now());
        if (selfieImage != null && !selfieImage.isBlank()) record.setSelfieImage(selfieImage);

        AttendanceLog saved = logRepository.save(record);
        try {
            webSocketHandler.broadcastAttendance(saved);
        } catch (Exception wsEx) {
            log.warn("WebSocket broadcast failed for remote entry (id={}): {}", saved.getId(), wsEx.getMessage());
        }

        
        String topic = MQTT_TOPIC_PREFIX + "/" + deviceCode + "/command";
        int openMs = 5000;
        String safeName = displayName.replace("\"", "").replace("\\", "");
        String mqttMsg = "{\"action\":\"OPEN_DOOR\"," +
                "\"duration_ms\":" + openMs + "," +
                "\"name\":\"" + safeName + "\"," +
                "\"relay\":true}";
        mqttGateway.sendToMqtt(mqttMsg, topic);
        scheduleDoorClose(topic, openMs + 500);
        log.info("Door opened for visitor: {} on device: {} (score: {})", displayName, deviceCode, faceResult.similarity);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("matched", true);
        result.put("score", faceResult.similarity);
        result.put("deviceCode", deviceCode);
        result.put("visitorName", displayName);
        result.put("logId", saved.getId());
        return result;
    }

    

    private void scheduleDoorClose(String topic, int delayMs) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(delayMs);
                String closeMsg = "{\"action\":\"CLOSE_DOOR\",\"relay\":true}";
                mqttGateway.sendToMqtt(closeMsg, topic);
                log.info("Sent CLOSE_DOOR to MQTT topic: {} (after {} ms)", topic, delayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("CLOSE_DOOR MQTT failed for topic {}: {}", topic, e.getMessage());
            }
        });
    }

    

    public void publishSyncData(String deviceCode) {
        
        List<Map<String, Object>> employees = permissionRepository.findByDeviceCode(deviceCode).stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .map(p -> {
                    Employee e = p.getEmployee();
                    Map<String, Object> emp = new HashMap<>();
                    String cccd = e.getCccd();
                    emp.put("cccd", cccd);  
                    emp.put("full_name", e.getFullName());
                    emp.put("is_active", 1);
                    emp.put("start_time", p.getStartTime() != null ? p.getStartTime().toString() : "06:00:00");
                    emp.put("end_time", p.getEndTime() != null ? p.getEndTime().toString() : "22:00:00");
                    return emp;
                })
                .collect(java.util.stream.Collectors.toList());

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("action", "SYNC_DATA");
            payload.put("employees", employees);
            String json = new ObjectMapper().writeValueAsString(payload);
            String topic = MQTT_TOPIC_PREFIX + "/" + deviceCode + "/command";
            mqttGateway.sendToMqtt(json, topic);
            log.info("Sent SYNC_DATA to device {} with {} employees", deviceCode, employees.size());
        } catch (Exception e) {
            log.error("Failed to publish SYNC_DATA to device {}", deviceCode, e);
        }
    }

    

    public AttendanceRecordResponseDTO processAttendanceWithLayer2Validation(AttendanceRecordRequestDTO request) {
        String cccd = request.getCccd();
        String deviceCode = request.getDeviceCode();

        
        boolean l1Passed   = Boolean.TRUE.equals(request.getLayer1Passed());
        double  l1Score    = request.getLayer1Score() != null ? request.getLayer1Score() : 0.0;
        String  l1Method   = request.getLayer1Method() != null ? request.getLayer1Method() : "LOCAL_FACE";

        
        if (request.getLayer1Passed() == null && request.getScore() != null) {
            l1Passed = Boolean.TRUE.equals(request.getMatched()) || request.getScore() >= faceThreshold;
            l1Score  = request.getScore();
        }

        if (!l1Passed) {
            
            
            
            log.info("L1 not passed (score={}) - proceeding to L2 for final decision: cccd={}", l1Score, cccd);
        }

        
        Optional<Employee> employeeOpt = employeeRepository.findByCccd(cccd);
        if (employeeOpt.isEmpty() && cccd != null && cccd.length() < 12) {
            java.util.List<Employee> candidates = employeeRepository.findByCccdEndingWith(cccd);
            if (candidates.size() == 1) {
                employeeOpt = Optional.of(candidates.get(0));
                log.info("CCCD short fallback: app={} → db={}", cccd, candidates.get(0).getCccd());
            } else if (candidates.size() > 1) {
                return buildFullDeniedResponse(request, l1Score, 0.0, 0.0,
                        true, false, false, "CCCD_AMBIGUOUS", "CCCD không rõ ràng");
            }
        }
        if (employeeOpt.isEmpty()) {
            return buildFullDeniedResponse(request, l1Score, 0.0, 0.0,
                    true, false, false, "CCCD_NOT_FOUND", "CCCD không tồn tại trong hệ thống");
        }

        Employee employee = employeeOpt.get();
        if (!Boolean.TRUE.equals(employee.getIsActive())) {
            return buildFullDeniedResponse(request, l1Score, 0.0, 0.0,
                    true, false, false, "EMPLOYEE_INACTIVE", "Tài khoản nhân viên đã bị vô hiệu");
        }

        
        if (employee.getActivationStatus() == ActivationStatus.PENDING_ACTIVATION) {
            
            String chipForActivation = request.getChipImage();
            if (chipForActivation != null && !chipForActivation.isBlank()) {
                log.info("FIRST_ACTIVATION - Trích xuất face vector từ chip CCCD: cccd={}", cccd);
                try {
                    FaceComparisonService.FaceVectorResult vr =
                            faceComparisonService.extractFaceVector(chipForActivation, employee.getCccd());
                    if (vr.success && vr.vectorBase64 != null) {
                        
                        String jsonVector = new String(Base64.getDecoder().decode(vr.vectorBase64), StandardCharsets.UTF_8);
                        employee.setFaceVector(jsonVector);
                        employee.setActivationStatus(ActivationStatus.ACTIVE);
                        employee.setFirstRegisteredAt(LocalDateTime.now());
                        employeeRepository.save(employee);
                        log.info("FIRST_ACTIVATION SUCCESS - cccd={}, quality={}", cccd,
                                String.format("%.3f", vr.quality));
                        
                    } else {
                        log.warn("FIRST_ACTIVATION FAILED - không extract được embedding: cccd={}", cccd);
                        return buildFullDeniedResponse(request, l1Score, 0.0, 0.0,
                                true, false, false, "ACTIVATION_FAILED",
                                "Không thể nhận diện khuôn mặt từ chip CCCD. Vui lòng thử lại.");
                    }
                } catch (Exception e) {
                    log.error("FIRST_ACTIVATION error: cccd={}, err={}", cccd, e.getMessage());
                    return buildFullDeniedResponse(request, l1Score, 0.0, 0.0,
                            true, false, false, "ACTIVATION_FAILED",
                            "Lỗi kích hoạt sinh trắc học. Vui lòng thử lại.");
                }
            } else {
                log.warn("DENIED - PENDING_ACTIVATION nhưng không có ảnh chip: cccd={}", cccd);
                return buildFullDeniedResponse(request, l1Score, 0.0, 0.0,
                        true, false, false, "PENDING_ACTIVATION",
                        "Vui lòng đặt chip CCCD lên để kích hoạt lần đầu.");
            }
        }
        if (employee.getActivationStatus() == ActivationStatus.SUSPENDED) {
            return buildFullDeniedResponse(request, l1Score, 0.0, 0.0,
                    true, false, false, "ACCOUNT_SUSPENDED", "Tài khoản đang bị đình chỉ.");
        }

        
        String  selfie    = request.getImageLive();
        String  chipPhoto = request.getChipImage();
        double  selfieVsDbScore = l1Score;
        log.info("L2a skipped (chip+selfie is primary auth): cccd={}", cccd);

        
        boolean isRemoteUnlock = Boolean.TRUE.equals(request.getRemoteUnlock());

        
        boolean selfieVsChipPassed = false;
        double  selfieVsChipScore  = 0.0;

        if (selfie != null && !selfie.isBlank() && chipPhoto != null && !chipPhoto.isBlank()) {
            try {
                FaceComparisonService.FaceComparisonResult r =
                        faceComparisonService.compareFaces(selfie, chipPhoto);
                if (!r.success) {
                    
                    log.warn("L2b chip comparison engine returned failure (trusted L1): {} | cccd={}", r.message, cccd);
                    selfieVsChipScore  = l1Score;
                    selfieVsChipPassed = l1Passed;
                } else {
                    selfieVsChipScore  = r.similarity;
                    
                    
                    double effectiveThreshold = isRemoteUnlock ? 0.20 : backupThreshold;
                    selfieVsChipPassed = r.similarity >= effectiveThreshold;
                    log.info("L2b selfie vs chip: cccd={}, score={}, threshold={}, passed={}, remoteUnlock={}",
                            cccd, String.format("%.4f", selfieVsChipScore), effectiveThreshold, selfieVsChipPassed, isRemoteUnlock);
                }
            } catch (Exception e) {
                log.warn("L2b chip comparison failed (trusted L1): {}", e.getMessage());
                selfieVsChipScore  = l1Score;
                selfieVsChipPassed = l1Passed; 
            }
        } else {
            
            selfieVsChipScore  = l1Score;
            selfieVsChipPassed = l1Passed;
            log.info("L2b skipped (no chip photo), trusted L1: passed={}", l1Passed);
        }

        if (!selfieVsChipPassed) {
            log.warn("DENIED - L2b selfie vs chip failed: cccd={}, chipScore={}, remoteUnlock={}", cccd, selfieVsChipScore, isRemoteUnlock);
            return buildFullDeniedResponse(request, l1Score, selfieVsDbScore, selfieVsChipScore,
                    true, true, false, "FACE_MISMATCH_CHIP",
                    "Khuôn mặt không khớp với ảnh chip CCCD tại server");
        }

        
        if (isRemoteUnlock) {
            
            log.info("REMOTE_UNLOCK mode - skipping permission check: cccd={}, device={}", cccd, deviceCode);
        } else {
        String dbCccd = employee.getCccd();
        Optional<AccessPermission> permOpt = permissionRepository.findByEmployeeCccdAndDeviceCode(dbCccd, deviceCode);
        if (permOpt.isEmpty()) {
            
            log.info("PENDING_APPROVAL - cccd={}, device={}: face verified but no access permission", dbCccd, deviceCode);
            return buildPendingApprovalResponse(request, employee,
                    l1Score, selfieVsDbScore, selfieVsChipScore, l1Method);
        }

        AccessPermission perm = permOpt.get();
        if (!Boolean.TRUE.equals(perm.getIsActive())) {
            return buildFullDeniedResponse(request, l1Score, selfieVsDbScore, selfieVsChipScore,
                    true, true, true, "PERMISSION_INACTIVE", "Quyền truy cập đã bị tắt");
        }

        LocalDate today = LocalDate.now();
        if (perm.getStartDate() != null && today.isBefore(perm.getStartDate())) {
            return buildFullDeniedResponse(request, l1Score, selfieVsDbScore, selfieVsChipScore,
                    true, true, true, "PERMISSION_NOT_VALID_YET", "Quyền truy cập chưa đến ngày hiệu lực");
        }
        if (perm.getEndDate() != null && today.isAfter(perm.getEndDate())) {
            return buildFullDeniedResponse(request, l1Score, selfieVsDbScore, selfieVsChipScore,
                    true, true, true, "PERMISSION_EXPIRED", "Quyền truy cập đã hết hạn");
        }

        
        if (perm.getStartTime() != null && perm.getEndTime() != null) {
            LocalTime now = LocalTime.now();
            if (now.isBefore(perm.getStartTime()) || now.isAfter(perm.getEndTime())) {
                log.warn("OUTSIDE_HOURS - CCCD={}, allowed={}-{}", cccd, perm.getStartTime(), perm.getEndTime());
                AttendanceLog alertLog = buildAttendanceLog(request, employee,
                        l1Score, selfieVsDbScore, selfieVsChipScore,
                        l1Passed, true, true, true,
                        "OUTSIDE_HOURS", false, l1Method);
                alertLog.setAlertType("OUTSIDE_HOURS");
                AttendanceLog saved = logRepository.save(alertLog);
                webSocketHandler.broadcastAttendance(saved);
                webSocketHandler.broadcastAlert(saved);
                return toResponseDTO(saved, employee, false, true, true, true,
                        l1Score, selfieVsDbScore, selfieVsChipScore, l1Method,
                        "OUTSIDE_HOURS",
                        "Ngoài khung giờ được phép: " + perm.getStartTime() + " - " + perm.getEndTime());
            }
        }
        } 

        
        double finalScore = Math.max(selfieVsDbScore, selfieVsChipScore);
        log.info("2-LAYER GRANTED - cccd={}, device={}, l2aScore={}, l2bScore={}, remoteUnlock={}",
                cccd, deviceCode, selfieVsDbScore, selfieVsChipScore, isRemoteUnlock);

        AttendanceLog record = buildAttendanceLog(request, employee,
                l1Score, selfieVsDbScore, selfieVsChipScore,
                l1Passed, true, true, true,
                "SUCCESS", true, l1Method);
        record.setScore(finalScore);
        record.setMatchScore(finalScore);
        if (isRemoteUnlock) {
            record.setComparisonMode("REMOTE_UNLOCK");
        }

        AttendanceLog saved = processAttendanceAndReply(record);

        
        
        final String chipPhotoFinal = chipPhoto;
        final Employee employeeFinal = employee;
        if (chipPhotoFinal != null && !chipPhotoFinal.isBlank()) {
            CompletableFuture.runAsync(() -> {
                try {
                    String storedVector = employeeFinal.getFaceVector();
                    boolean needUpdate;
                    if (storedVector == null || storedVector.isBlank()) {
                        needUpdate = true;
                    } else {
                        
                        FaceComparisonService.FaceComparisonResult chipVsStored =
                                faceComparisonService.compareFaceWithVector(chipPhotoFinal, storedVector);
                        needUpdate = chipVsStored.success && chipVsStored.similarity < 0.50;
                        if (needUpdate) {
                            log.info("CHIP_RENEWAL detected: cccd={}, chipVsStored={} < 0.50 → update vector mới",
                                    employeeFinal.getCccd(), String.format("%.3f", chipVsStored.similarity));
                        }
                    }
                    if (needUpdate) {
                        FaceComparisonService.FaceVectorResult vr =
                                faceComparisonService.extractFaceVector(chipPhotoFinal, employeeFinal.getCccd());
                        if (vr.success && vr.vectorBase64 != null) {
                            
                            String jsonVectorRenewal = new String(Base64.getDecoder().decode(vr.vectorBase64), StandardCharsets.UTF_8);
                            employeeFinal.setFaceVector(jsonVectorRenewal);
                            employeeRepository.save(employeeFinal);
                            log.info("CHIP_RENEWAL updated faceVector: cccd={}, quality={}",
                                    employeeFinal.getCccd(), String.format("%.3f", vr.quality));
                        }
                    }
                } catch (Exception e) {
                    log.debug("Chip renewal check (non-critical): cccd={}, err={}",
                            employeeFinal.getCccd(), e.getMessage());
                }
            });
        }

        return toResponseDTO(saved, employee, true, true, true, true,
                l1Score, selfieVsDbScore, selfieVsChipScore, l1Method,
                "SUCCESS", "Điểm danh thành công - Xác thực 2 lớp hoàn tất");
    }

    
    private AttendanceLog buildAttendanceLog(AttendanceRecordRequestDTO req, Employee emp,
            double l1Score, double selfieVsDbScore, double selfieVsChipScore,
            boolean l1Passed, boolean selfieVsDbPassed, boolean selfieVsChipPassed, boolean chipVerified,
            String status, boolean accessGranted, String l1Method) {
        AttendanceLog log2 = new AttendanceLog();
        log2.setCccd(emp.getCccd());
        log2.setFullName(emp.getFullName());
        log2.setEmployeeName(emp.getFullName());
        log2.setCapturedName(req.getCapturedName() != null ? req.getCapturedName() : emp.getFullName());
        log2.setDeviceCode(req.getDeviceCode());
        log2.setScore(selfieVsDbScore > 0 ? selfieVsDbScore : l1Score);
        log2.setMatchScore(selfieVsDbScore > 0 ? selfieVsDbScore : l1Score);
        log2.setMatched(accessGranted);
        log2.setAccessGranted(accessGranted);
        log2.setComparisonMethod("DUAL_LAYER");
        log2.setComparisonMode("L1:" + l1Method + "+L2:SERVER");
        log2.setStatus(status);
        log2.setSelfieImage(req.getImageLive());
        log2.setScanTime(LocalDateTime.now());
        log2.setEmployee(emp);
        
        log2.setLayer1Passed(l1Passed);
        log2.setLayer1Score(l1Score);
        log2.setLayer1Method(l1Method);
        log2.setSelfieVsDbPassed(selfieVsDbPassed);
        log2.setSelfieVsDbScore(selfieVsDbScore);
        log2.setSelfieVsChipPassed(selfieVsChipPassed);
        log2.setSelfieVsChipScore(selfieVsChipScore);
        log2.setChipVerified(chipVerified);
        return log2;
    }

    
    private AttendanceRecordResponseDTO toResponseDTO(AttendanceLog saved, Employee emp,
            boolean accessGranted, boolean l1Auth, boolean selfieVsDb, boolean selfieVsChip,
            double l1Score, double selfieVsDbScore, double selfieVsChipScore, String l1Method,
            String status, String message) {
        double finalScore = Math.max(selfieVsDbScore, selfieVsChipScore);
        return AttendanceRecordResponseDTO.builder()
                .attendanceId(saved.getId())
                .cccd(saved.getCccd())
                .fullName(emp.getFullName())
                .deviceCode(saved.getDeviceCode())
                .accessGranted(accessGranted)
                .layer1Authorized(l1Auth)
                .layer1Score(l1Score)
                .layer1Method(l1Method)
                .layer2Authorized(accessGranted)
                .selfieVsDbPassed(selfieVsDb)
                .selfieVsDbScore(selfieVsDbScore)
                .selfieVsChipPassed(selfieVsChip)
                .selfieVsChipScore(selfieVsChipScore)
                .chipVerified(saved.getChipVerified())
                .authorizationReason(status)
                .matchScore(finalScore)
                .faceMatched(selfieVsDb && selfieVsChip)
                .comparisonMethod("DUAL_LAYER")
                .doorOpenSignalSent(accessGranted)
                .mqttTopic(accessGranted ? MQTT_TOPIC_PREFIX + "/" + saved.getDeviceCode() + "/command" : null)
                .recordedAt(saved.getScanTime())
                .processedAt(LocalDateTime.now())
                .status(status)
                .message(message)
                .build();
    }

    
    private AttendanceRecordResponseDTO buildFullDeniedResponse(
            AttendanceRecordRequestDTO request,
            double l1Score, double selfieVsDbScore, double selfieVsChipScore,
            boolean chipVerified, boolean selfieVsDb, boolean selfieVsChip,
            String reason, String message) {
        String compMode = request.getMethod() != null ? request.getMethod() : "APP_OFFLINE";
        Employee employee = null;
        if (request.getCccd() != null) {
            Optional<Employee> empOpt = employeeRepository.findByCccd(request.getCccd());
            if (empOpt.isEmpty() && request.getCccd().length() < 12) {
                java.util.List<Employee> candidates = employeeRepository.findByCccdEndingWith(request.getCccd());
                if (candidates.size() == 1) empOpt = Optional.of(candidates.get(0));
            }
            employee = empOpt.orElse(null);
        }

        AttendanceLog record = new AttendanceLog();
        
        record.setCccd(employee != null ? employee.getCccd() : request.getCccd());
        record.setFullName(employee != null ? employee.getFullName() : null);
        record.setEmployeeName(employee != null ? employee.getFullName() : null);
        record.setCapturedName(request.getCapturedName());
        record.setDeviceCode(request.getDeviceCode());
        double scoreForLog = selfieVsDbScore > 0 ? selfieVsDbScore : l1Score;
        record.setScore(scoreForLog);
        record.setMatchScore(scoreForLog);
        record.setMatched(false);
        record.setAccessGranted(false);
        record.setComparisonMethod("DUAL_LAYER");
        record.setComparisonMode(compMode);
        record.setStatus("DENIED");
        record.setSelfieImage(request.getImageLive());
        record.setScanTime(LocalDateTime.now());
        record.setLayer1Passed(Boolean.TRUE.equals(request.getLayer1Passed()));
        record.setLayer1Score(l1Score);
        record.setSelfieVsDbPassed(selfieVsDb);
        record.setSelfieVsDbScore(selfieVsDbScore);
        record.setSelfieVsChipPassed(selfieVsChip);
        record.setSelfieVsChipScore(selfieVsChipScore);
        record.setChipVerified(chipVerified);
        if (employee != null) record.setEmployee(employee);

        AttendanceLog saved = logRepository.save(record);
        try {
            webSocketHandler.broadcastAttendance(saved);
        } catch (Exception wsEx) {
            log.warn("WebSocket broadcast failed for denied record id={}: {}", saved.getId(), wsEx.getMessage());
        }

        String employeeFullName = employee != null ? employee.getFullName() : null;
        return AttendanceRecordResponseDTO.builder()
                .attendanceId(saved.getId())
                .cccd(saved.getCccd())
                .fullName(employeeFullName)
                .deviceCode(request.getDeviceCode())
                .accessGranted(false)
                .layer1Authorized(Boolean.TRUE.equals(request.getLayer1Passed()))
                .layer1Score(l1Score)
                .layer2Authorized(false)
                .selfieVsDbPassed(selfieVsDb)
                .selfieVsDbScore(selfieVsDbScore)
                .selfieVsChipPassed(selfieVsChip)
                .selfieVsChipScore(selfieVsChipScore)
                .chipVerified(chipVerified)
                .authorizationReason(reason)
                .matchScore(scoreForLog)
                .faceMatched(false)
                .doorOpenSignalSent(false)
                .recordedAt(saved.getScanTime())
                .processedAt(LocalDateTime.now())
                .status("DENIED")
                .message(message)
                .build();
    }

    private AttendanceRecordResponseDTO buildDeniedResponse(AttendanceRecordRequestDTO request, double score, String reason, String message) {
        return buildFullDeniedResponse(request, score, 0.0, 0.0, false, false, false, reason, message);
    }

    
    private AttendanceRecordResponseDTO buildPendingApprovalResponse(
            AttendanceRecordRequestDTO request, Employee employee,
            double l1Score, double selfieVsDbScore, double selfieVsChipScore, String l1Method) {
        AttendanceLog record = buildAttendanceLog(request, employee,
                l1Score, selfieVsDbScore, selfieVsChipScore,
                true, true, true, true,
                "PENDING_APPROVAL", false, l1Method);
        record.setAlertType("PENDING_APPROVAL");
        AttendanceLog saved = logRepository.save(record);
        webSocketHandler.broadcastAttendance(saved);
        webSocketHandler.broadcastPendingAccess(saved);

        return AttendanceRecordResponseDTO.builder()
                .attendanceId(saved.getId())
                .cccd(employee.getCccd())
                .fullName(employee.getFullName())
                .deviceCode(request.getDeviceCode())
                .accessGranted(false)
                .layer1Authorized(true)
                .layer1Score(l1Score)
                .layer1Method(l1Method)
                .layer2Authorized(true)
                .selfieVsDbPassed(true)
                .selfieVsDbScore(selfieVsDbScore)
                .selfieVsChipPassed(selfieVsChipScore > 0)
                .selfieVsChipScore(selfieVsChipScore)
                .chipVerified(true)
                .authorizationReason("PENDING_APPROVAL")
                .matchScore(Math.max(selfieVsDbScore, l1Score))
                .faceMatched(true)
                .comparisonMethod("DUAL_LAYER")
                .doorOpenSignalSent(false)
                .recordedAt(saved.getScanTime())
                .processedAt(LocalDateTime.now())
                .status("PENDING_APPROVAL")
                .message("Khuôn mặt đã xác minh. Đang chờ phê duyệt từ nhân viên quản lý...")
                .build();
    }

    

    public Map<String, Object> approveAccess(Long logId) {
        Optional<AttendanceLog> logOpt = logRepository.findById(logId);
        if (logOpt.isEmpty()) {
            return Map.of("success", false, "message", "Không tìm thấy yêu cầu id=" + logId);
        }
        AttendanceLog record = logOpt.get();
        if (!"PENDING_APPROVAL".equals(record.getStatus())) {
            return Map.of("success", false, "message", "Yêu cầu không ở trạng thái chờ phê duyệt");
        }
        
        record.setStatus("APPROVED");
        record.setAlertType(null);
        record.setAccessGranted(true);
        record.setMatched(true);
        logRepository.save(record);

        
        String topic = MQTT_TOPIC_PREFIX + "/" + record.getDeviceCode() + "/command";
        int openMs = 5000;
        String safeName = (record.getCapturedName() != null ? record.getCapturedName() : "Khách")
                .replace("\"", "").replace("\\", "");
        String msg = "{\"action\":\"OPEN_DOOR\",\"duration_ms\":" + openMs +
                ",\"name\":\"" + safeName + "\",\"relay\":true,\"approved\":true}";
        try {
            mqttGateway.sendToMqtt(msg, topic);
            scheduleDoorClose(topic, openMs + 500);
            log.info("APPROVED → sent OPEN_DOOR to {} for id={}", topic, logId);
        } catch (Exception e) {
            log.warn("approveAccess: MQTT failed for id={}: {}", logId, e.getMessage());
        }

        
        webSocketHandler.broadcastAttendance(record);
        return Map.of("success", true, "message", "Đã mở cửa cho " + safeName, "logId", logId);
    }

    public boolean validateFaceAttendance(String selfieBase64, String storedFaceBase64) {
        if (selfieBase64 == null || storedFaceBase64 == null) return false;
        try {
            FaceComparisonService.FaceComparisonResult result = faceComparisonService.compareFaces(selfieBase64, storedFaceBase64);
            return result.success && result.similarity >= faceThreshold;
        } catch (Exception e) {
            log.error("Error validating face attendance", e);
            return false;
        }
    }

    public double getFaceAuthenticationScore(String selfieBase64, String storedFaceBase64) {
        try {
            FaceComparisonService.FaceComparisonResult result = faceComparisonService.compareFaces(selfieBase64, storedFaceBase64);
            return result.success ? result.similarity : 0.0;
        } catch (Exception e) {
            log.error("Error getting face authentication score", e);
            return 0.0;
        }
    }
}