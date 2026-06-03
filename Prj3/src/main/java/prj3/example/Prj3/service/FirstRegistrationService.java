package prj3.example.Prj3.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import prj3.example.Prj3.dto.FirstRegistrationRequestDTO;
import prj3.example.Prj3.entity.AccessPermission;
import prj3.example.Prj3.entity.Employee;
import prj3.example.Prj3.enums.ActivationStatus;
import prj3.example.Prj3.enums.PersonType;
import prj3.example.Prj3.repository.AccessPermissionRepository;
import prj3.example.Prj3.repository.EmployeeRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class FirstRegistrationService {

    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private AccessPermissionRepository permissionRepository;
    @Autowired private FaceAIService faceAIService;
    @Autowired private FaceComparisonService faceComparisonService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    

    @Transactional
    public Map<String, Object> registerFirstTime(FirstRegistrationRequestDTO req) {
        Map<String, Object> result = new HashMap<>();

        
        Employee employee = resolveEmployee(req);
        if (employee == null) {
            result.put("success", false);
            result.put("message", "Không tìm thấy nhân viên với ID/CCCD đã cung cấp");
            return result;
        }

        
        if (employee.getActivationStatus() == ActivationStatus.ACTIVE) {
            result.put("success", false);
            result.put("message", "Nhân viên đã được kích hoạt trước đó. Nếu cần đăng ký lại, hãy liên hệ Admin.");
            result.put("activationStatus", ActivationStatus.ACTIVE);
            return result;
        }
        if (employee.getActivationStatus() == ActivationStatus.SUSPENDED) {
            result.put("success", false);
            result.put("message", "Tài khoản nhân viên đang bị đình chỉ. Liên hệ Admin để khôi phục.");
            result.put("activationStatus", ActivationStatus.SUSPENDED);
            return result;
        }

        
        if (req.getSelfieImageBase64() == null || req.getSelfieImageBase64().isBlank()) {
            result.put("success", false);
            result.put("message", "Thiếu ảnh selfie. Vui lòng chụp ảnh khuôn mặt tại trạm.");
            return result;
        }

        
        if (req.getCccdChipImageBase64() != null && !req.getCccdChipImageBase64().isBlank()) {
            try {
                FaceComparisonService.FaceComparisonResult cmp =
                        faceComparisonService.compareFaces(req.getSelfieImageBase64(), req.getCccdChipImageBase64());
                if (!cmp.matched) {
                    log.warn("First-reg DENIED - selfie vs chip mismatch: cccd={}, score={}", employee.getCccd(), cmp.similarity);
                    result.put("success", false);
                    result.put("message", "Khuôn mặt không khớp với ảnh trên chip CCCD (score=" + cmp.similarity + ")");
                    result.put("similarity", cmp.similarity);
                    return result;
                }
                log.info("First-reg selfie vs chip PASSED: cccd={}, score={}", employee.getCccd(), cmp.similarity);
                result.put("chipMatchScore", cmp.similarity);
            } catch (Exception e) {
                log.warn("Chip photo comparison failed, proceeding without it: {}", e.getMessage());
            }
        }

        
        try {
            double[] vector = faceAIService.extractVector(req.getSelfieImageBase64());
            if (vector == null || vector.length == 0) {
                result.put("success", false);
                result.put("message", "Không thể trích xuất đặc trưng khuôn mặt từ ảnh selfie. Hãy thử lại.");
                return result;
            }
            String vectorJson = objectMapper.writeValueAsString(vector);
            employee.setFaceVector(vectorJson);
            log.info("Face vector extracted and saved: cccd={}, dims={}", employee.getCccd(), vector.length);
        } catch (Exception e) {
            log.error("Face vector extraction failed: cccd={}", employee.getCccd(), e);
            result.put("success", false);
            result.put("message", "Lỗi trích xuất face vector: " + e.getMessage());
            return result;
        }

        
        if (req.getCccdChipImageBase64() != null && !req.getCccdChipImageBase64().isBlank()) {
            employee.setImageRawUrl(req.getCccdChipImageBase64());
        }

        
        List<AccessPermission> perms = permissionRepository.findByEmployeeId(employee.getId());
        int activatedCount = 0;
        for (AccessPermission p : perms) {
            if (!Boolean.TRUE.equals(p.getIsActive())) {
                p.setIsActive(true);
                permissionRepository.save(p);
                activatedCount++;
            }
        }
        log.info("Activated {} access permissions for employee id={}", activatedCount, employee.getId());

        
        employee.setActivationStatus(ActivationStatus.ACTIVE);
        employee.setFirstRegisteredAt(LocalDateTime.now());
        employeeRepository.save(employee);

        log.info("Employee ACTIVATED: id={}, cccd={}, name={}, station={}",
                employee.getId(), employee.getCccd(), employee.getFullName(), req.getStationDeviceCode());

        result.put("success", true);
        result.put("message", "Đăng ký lần đầu thành công! Nhân viên đã được kích hoạt.");
        result.put("employeeId", employee.getId());
        result.put("fullName", employee.getFullName());
        result.put("activationStatus", ActivationStatus.ACTIVE);
        result.put("permissionsActivated", activatedCount);
        result.put("firstRegisteredAt", employee.getFirstRegisteredAt());
        return result;
    }

    
    private Employee resolveEmployee(FirstRegistrationRequestDTO req) {
        if (req.getEmployeeId() != null) {
            Optional<Employee> opt = employeeRepository.findById(req.getEmployeeId());
            if (opt.isPresent()) return opt.get();
        }
        if (req.getCccd() != null && !req.getCccd().isBlank()) {
            return employeeRepository.findByCccd(req.getCccd()).orElse(null);
        }
        return null;
    }

    

    @Transactional
    public Map<String, Object> deactivateEmployee(Long employeeId, String reason, boolean eraseBiometrics) {
        Map<String, Object> result = new HashMap<>();
        Optional<Employee> opt = employeeRepository.findById(employeeId);
        if (opt.isEmpty()) {
            result.put("success", false);
            result.put("message", "Không tìm thấy nhân viên ID=" + employeeId);
            return result;
        }
        Employee emp = opt.get();

        
        List<AccessPermission> perms = permissionRepository.findByEmployeeId(employeeId);
        int revokedCount = 0;
        for (AccessPermission p : perms) {
            if (Boolean.TRUE.equals(p.getIsActive())) {
                p.setIsActive(false);
                permissionRepository.save(p);
                revokedCount++;
            }
        }

        
        boolean shouldErase = eraseBiometrics
                || emp.getPersonType() == PersonType.CANDIDATE
                || emp.getPersonType() == PersonType.VISITOR;

        if (shouldErase) {
            emp.setFaceVector(null);
            emp.setImageRawUrl(null);
            emp.setBackupPhotoEmbedding(null);
            log.info("Biometric data ERASED for {} id={}, personType={}",
                    emp.getFullName(), employeeId, emp.getPersonType());
        }

        emp.setActivationStatus(ActivationStatus.SUSPENDED);
        employeeRepository.save(emp);

        log.info("Employee DEACTIVATED: id={}, name={}, reason='{}', biometricsErased={}, permissionsRevoked={}",
                employeeId, emp.getFullName(), reason, shouldErase, revokedCount);

        result.put("success", true);
        result.put("message", "Tài khoản đã bị đình chỉ.");
        result.put("employeeId", employeeId);
        result.put("fullName", emp.getFullName());
        result.put("personType", emp.getPersonType());
        result.put("activationStatus", ActivationStatus.SUSPENDED);
        result.put("permissionsRevoked", revokedCount);
        result.put("biometricsErased", shouldErase);
        result.put("reason", reason);
        return result;
    }

    

    @Transactional
    public Map<String, Object> reactivateEmployee(Long employeeId, String reason) {
        Map<String, Object> result = new HashMap<>();
        Optional<Employee> opt = employeeRepository.findById(employeeId);
        if (opt.isEmpty()) {
            result.put("success", false);
            result.put("message", "Không tìm thấy nhân viên ID=" + employeeId);
            return result;
        }
        Employee emp = opt.get();
        if (emp.getActivationStatus() != ActivationStatus.SUSPENDED) {
            result.put("success", false);
            result.put("message", "Nhân viên không ở trạng thái SUSPENDED.");
            return result;
        }

        
        boolean needsReregistration = (emp.getFaceVector() == null || emp.getFaceVector().isBlank());
        if (needsReregistration) {
            emp.setActivationStatus(ActivationStatus.PENDING_ACTIVATION);
            employeeRepository.save(emp);
            log.info("Employee set to PENDING_ACTIVATION (biometrics missing): id={}", employeeId);
            result.put("success", true);
            result.put("message", "Tài khoản được đặt về PENDING_ACTIVATION. Cần đăng ký sinh trắc học lại tại trạm.");
            result.put("activationStatus", ActivationStatus.PENDING_ACTIVATION);
            result.put("requiresReregistration", true);
            return result;
        }

        
        List<AccessPermission> perms = permissionRepository.findByEmployeeId(employeeId);
        int restoredCount = 0;
        for (AccessPermission p : perms) {
            p.setIsActive(true);
            permissionRepository.save(p);
            restoredCount++;
        }

        emp.setActivationStatus(ActivationStatus.ACTIVE);
        employeeRepository.save(emp);

        log.info("Employee REACTIVATED: id={}, name={}, reason='{}', permissionsRestored={}",
                employeeId, emp.getFullName(), reason, restoredCount);

        result.put("success", true);
        result.put("message", "Tài khoản đã được khôi phục.");
        result.put("activationStatus", ActivationStatus.ACTIVE);
        result.put("permissionsRestored", restoredCount);
        result.put("requiresReregistration", false);
        return result;
    }
}
