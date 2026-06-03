package prj3.example.Prj3.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeCreateDTO {
    
    @NotBlank(message = "CCCD is required")
    @Size(min = 9, max = 12, message = "CCCD must be 9-12 characters")
    @Pattern(regexp = "^[0-9]+$", message = "CCCD must contain only digits")
    private String cccd;
    
    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Name must be 2-100 characters")
    private String fullName;
    
    private String department;
    private String position;
    
    @Email(message = "Email must be valid")
    @Size(max = 255)
    private String email;

    @Pattern(regexp = "^$|^[0-9\\-\\+\\s\\(\\)]+$", message = "Phone must be valid")
    private String phone;
    
    private Long organizationId; 
    
    @Builder.Default
    private Boolean isActive = true;
    
    
    private String faceVectorBase64;
    
    
    private String backupPhotoBase64;
}
