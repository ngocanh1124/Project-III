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
public class APIResponseDTO<T> {
    private Boolean success;
    private String message;
    private T data;
    private String errorCode;
    private String errorDetails;
    private LocalDateTime timestamp;
    
    public static <T> APIResponseDTO<T> ok(T data, String message) {
        return APIResponseDTO.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static <T> APIResponseDTO<T> error(String errorCode, String message, String details) {
        return APIResponseDTO.<T>builder()
                .success(false)
                .errorCode(errorCode)
                .message(message)
                .errorDetails(details)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
