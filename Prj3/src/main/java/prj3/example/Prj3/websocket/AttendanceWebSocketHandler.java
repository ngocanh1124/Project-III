package prj3.example.Prj3.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import prj3.example.Prj3.entity.AttendanceLog;

@Component
public class AttendanceWebSocketHandler {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    
    public void broadcastAttendance(AttendanceLog log) {
        try {
            messagingTemplate.convertAndSend("/topic/attendance", log);
            System.out.println(">>> Đã đẩy Log của " + log.getCapturedName() + " lên Web Dashboard");
        } catch (Exception e) {
            System.out.println(">>> WebSocket broadcast failed (non-fatal): " + e.getMessage());
        }
    }

    
    public void broadcastAlert(AttendanceLog log) {
        try {
            messagingTemplate.convertAndSend("/topic/alert", log);
            System.out.println(">>> ALERT broadcast: " + log.getAlertType() + " - " + log.getCapturedName());
        } catch (Exception e) {
            System.out.println(">>> Alert broadcast failed (non-fatal): " + e.getMessage());
        }
    }

    
    public void broadcastPendingAccess(AttendanceLog log) {
        try {
            messagingTemplate.convertAndSend("/topic/pending-access", log);
            System.out.println(">>> PENDING_ACCESS broadcast: " + log.getCapturedName() + " id=" + log.getId());
        } catch (Exception e) {
            System.out.println(">>> Pending access broadcast failed (non-fatal): " + e.getMessage());
        }
    }

    
    public void broadcastDeviceLog(java.util.Map<String, Object> data) {
        try {
            messagingTemplate.convertAndSend("/topic/device-log", data);
        } catch (Exception e) {
            System.out.println(">>> Device log broadcast failed (non-fatal): " + e.getMessage());
        }
    }
}