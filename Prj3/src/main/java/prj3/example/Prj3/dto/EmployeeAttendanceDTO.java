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
public class EmployeeAttendanceDTO {
    private String cccd;
    private String fullName;
    private Integer totalDays;
    private Integer presentDays;
    private Integer absentDays;
    private Double attendanceRate;
    private LocalDateTime lastScan;
    private String status;  
}
