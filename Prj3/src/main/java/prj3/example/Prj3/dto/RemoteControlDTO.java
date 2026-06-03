package prj3.example.Prj3.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RemoteControlDTO {
    private String action;   
    private String message;  
}