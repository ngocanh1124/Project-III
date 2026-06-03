package prj3.example.Prj3.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String cccd;
    private String capturedName;
    private String employeeName;  
    private String fullName;      
    private String deviceCode;

    @Column(name = "image_live_url", columnDefinition = "TEXT")
    private String imageLiveUrl; 
    
    @Column(name = "selfie_image", columnDefinition = "LONGTEXT")
    private String selfieImage;  

    private Double score;           
    private Double matchScore;      
    private Boolean matched;        
    private Boolean accessGranted;  

    
    
    @Column(name = "layer1_passed")
    private Boolean layer1Passed;
    @Column(name = "layer1_score")
    private Double layer1Score;
    @Column(name = "layer1_method", length = 30)
    private String layer1Method;

    
    @Column(name = "selfie_vs_db_passed")
    private Boolean selfieVsDbPassed;
    @Column(name = "selfie_vs_db_score")
    private Double selfieVsDbScore;

    
    @Column(name = "selfie_vs_chip_passed")
    private Boolean selfieVsChipPassed;
    @Column(name = "selfie_vs_chip_score")
    private Double selfieVsChipScore;

    
    @Column(name = "chip_verified")
    private Boolean chipVerified;
    
    private String method;          
    private String comparisonMethod; 
    private String comparisonMode;  
    private String status;          
    private String alertType;       
    
    @Builder.Default
    private LocalDateTime scanTime = LocalDateTime.now();
    
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = true)
    private Employee employee;      
    
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = true)
    private Organization organization;  
}