package prj3.example.Prj3.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeDTO {
    private Long id;
    private String cccd;
    private String fullName;
    private String department;
    private String position;
    private String email;
    private String phone;
    private Boolean isActive;
    private String imageRawUrl;
    private LocalDateTime createdAt;
}

