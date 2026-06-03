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
public class AttendanceRecordResponseDTO {
    
    private Long attendanceId;
    private String cccd;
    private String fullName;
    private String deviceCode;
    private Boolean accessGranted;
    
    
    private Boolean layer1Authorized;       
    private Double  layer1Score;            
    private String  layer1Method;           

    
    private Boolean layer2Authorized;       
    private Boolean selfieVsDbPassed;       
    private Double  selfieVsDbScore;        
    private Boolean selfieVsChipPassed;     
    private Double  selfieVsChipScore;      
    private Boolean chipVerified;           

    private String authorizationReason;

    
    private Double matchScore;
    private Boolean faceMatched;
    private String comparisonMethod;
    
    
    private Boolean doorOpenSignalSent;
    private String mqttTopic;
    
    
    private LocalDateTime recordedAt;
    private LocalDateTime processedAt;
    
    
    private String status;  
    private String message;
    private String errorDetails;
}
