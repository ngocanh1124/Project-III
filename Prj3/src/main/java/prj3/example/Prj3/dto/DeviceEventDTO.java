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
public class DeviceEventDTO {
    
    private String cccd;

    
    private String capturedName;

    
    private String chipImageBase64;

    
    private String imageLiveBase64;

    
    private Double score;

    
    private Boolean matched;

    
    private String method;

    
    private LocalDateTime timestamp;
}
