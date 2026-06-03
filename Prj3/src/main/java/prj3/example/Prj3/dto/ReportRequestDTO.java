package prj3.example.Prj3.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportRequestDTO {
    private String startDate;
    private String endDate;
    private String deviceCode;
    private String cccd;
    private Boolean successOnly;
    private String format;  
}
