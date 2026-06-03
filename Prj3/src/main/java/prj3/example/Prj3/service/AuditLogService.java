package prj3.example.Prj3.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import prj3.example.Prj3.entity.AuditLog;
import prj3.example.Prj3.repository.AuditLogRepository;

import java.time.LocalDateTime;

@Slf4j
@Service
public class AuditLogService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    

    @Async("asyncFaceExecutor")
    public void log(String actor, String action,
                    String resourceType, String resourceId,
                    String outcome, String detail) {
        log(actor, action, resourceType, resourceId, outcome, detail, null, null, null);
    }

    @Async("asyncFaceExecutor")
    public void log(String actor, String action,
                    String resourceType, String resourceId,
                    String outcome, String detail,
                    String ipAddress, String userAgent,
                    Long organizationId) {
        try {
            AuditLog entry = AuditLog.builder()
                    .actor(actor != null ? actor : "ANONYMOUS")
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .outcome(outcome)
                    .detail(detail)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent != null && userAgent.length() > 200
                               ? userAgent.substring(0, 200) : userAgent)
                    .createdAt(LocalDateTime.now())
                    .build();

            
            if (organizationId != null) {
                prj3.example.Prj3.entity.Organization org = new prj3.example.Prj3.entity.Organization();
                org.setId(organizationId);
                entry.setOrganization(org);
            }

            auditLogRepository.save(entry);
        } catch (Exception e) {
            
            log.error("Failed to write audit log: actor={}, action={}", actor, action, e);
        }
    }

    

    public static String extractIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
