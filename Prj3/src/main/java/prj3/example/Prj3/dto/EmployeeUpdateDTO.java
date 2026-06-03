package prj3.example.Prj3.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeUpdateDTO {
    private String fullName;
    private String department;
    private String position;
    private String email;
    private String phone;
    private Boolean isActive;
    private String faceVectorBase64;
    private String backupPhotoBase64;
}
