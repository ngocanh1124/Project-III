package prj3.example.Prj3.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class HistoryResponseDTO {
    private Long id;
    private String cccd;
    private String capturedName;
    private String deviceCode;
    private String imageLiveUrl; 
    private LocalDateTime scanTime;
    private String status;
    private Double score;
}