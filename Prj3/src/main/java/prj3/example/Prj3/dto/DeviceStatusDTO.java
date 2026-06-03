package prj3.example.Prj3.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DeviceStatusDTO {
    private String deviceCode;
    private String locationName;
    private Boolean isOnline;
    private LocalDateTime lastSync;
}