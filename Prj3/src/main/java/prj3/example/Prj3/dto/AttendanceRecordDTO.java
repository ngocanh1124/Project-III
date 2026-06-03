package prj3.example.Prj3.dto;

import lombok.Data;

@Data
public class AttendanceRecordDTO {
    private String cccd;
    private String capturedName;
    private String deviceCode;
    private String imageLive; 
    private Double score;
    private String method; 
}