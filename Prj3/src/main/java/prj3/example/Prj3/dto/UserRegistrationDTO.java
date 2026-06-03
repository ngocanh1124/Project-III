package prj3.example.Prj3.dto;

import lombok.Data;

@Data
public class UserRegistrationDTO {
    private String username;
    private String password;
    private String fullName;
    private String email;
    
    private String organizationCode;
}