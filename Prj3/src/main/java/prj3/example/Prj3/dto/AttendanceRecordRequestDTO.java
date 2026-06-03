package prj3.example.Prj3.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonAlias;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceRecordRequestDTO {

    
    @NotBlank(message = "Device code is required")
    @Size(min = 3, max = 50, message = "Device code must be 3-50 characters")
    @JsonAlias({"device_code", "deviceId", "device_id"})
    private String deviceCode;

    
    @Positive(message = "Organization ID must be positive")
    @JsonAlias({"companyId", "organization_id", "org_id"})
    private Long organizationId;

    
    @NotBlank(message = "CCCD is required")
    @Size(min = 9, max = 12, message = "CCCD must be 9-12 characters")
    @Pattern(regexp = "^[0-9]+$", message = "CCCD must contain only digits")
    private String cccd;

    @JsonAlias({"captured_name", "name", "full_name"})
    private String capturedName;

    
    
    @JsonAlias({"chip_image", "chipImage", "faceImage", "face_image",
                "image_chip", "imageChip", "chipImageBase64"})
    private String chipImage;

    
    @JsonAlias({"selfie", "selfie_image", "selfieImage", "liveImage",
                "live_image", "image_live", "faceImageLive", "imageSelfie", "image_selfie"})
    private String imageLive;

    
    
    @JsonAlias({"layer1_passed", "l1Passed"})
    private Boolean layer1Passed;

    
    @DecimalMin(value = "0.0", message = "Layer1 score must be >= 0")
    @DecimalMax(value = "1.0", message = "Layer1 score must be <= 1")
    @JsonAlias({"layer1_score", "l1Score"})
    private Double layer1Score;

    
    @JsonAlias({"layer1_method", "l1Method"})
    private String layer1Method;

    
    @DecimalMin(value = "0.0", message = "Score must be >= 0")
    @DecimalMax(value = "1.0", message = "Score must be <= 1")
    private Double score;

    private Boolean matched;

    private String method;

    private LocalDateTime timestamp;

    @Builder.Default
    private String apiVersion = "v2.0";

    
    @JsonAlias({"remote_unlock"})
    private Boolean remoteUnlock;

    @Override
    public String toString() {
        return "AttendanceRecordRequest{" +
                "deviceCode='" + deviceCode + '\'' +
                ", cccd='" + cccd + '\'' +
                ", capturedName='" + capturedName + '\'' +
                ", layer1Passed=" + layer1Passed +
                ", layer1Score=" + layer1Score +
                ", score=" + score +
                ", matched=" + matched +
                ", method='" + method + '\'' +
                '}';
    }
}
