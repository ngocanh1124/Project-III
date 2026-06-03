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
public class AttendanceHistoryDTO {
    private Long id;
    private String cccd;
    private String fullName;
    private String deviceCode;
    private String deviceLocation;
    private LocalDateTime scanTime;
    private Boolean accessGranted;
    private Double matchScore;
    private String comparisonMethod;
    private String status;  
    private String alertType; 
    private String photo_url;  
    private String selfieImage;  
}
