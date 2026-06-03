package prj3.example.Prj3.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceDashboardDTO {
    private String deviceCode;
    private String location;
    private String status;    
    private LocalDateTime lastSeen;
    private Long todayAttempts;
    private Long todaySuccess;
    private Double todaySuccessRate;
    private Long monthAttempts;
    private Long monthSuccess;
    private Double monthSuccessRate;
    private String ipAddress;
}
