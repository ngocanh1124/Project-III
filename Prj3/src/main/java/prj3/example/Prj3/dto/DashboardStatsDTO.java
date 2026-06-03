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
public class DashboardStatsDTO {
    private Long totalAttempts;
    private Long successfulAttempts;
    private Long deniedAttempts;
    private Long failedMatches;
    private Double successRate;
    private Long activeEmployees;
    private Long totalDevices;
    private Long onlineDevices;
    private LocalDateTime lastUpdated;
}
