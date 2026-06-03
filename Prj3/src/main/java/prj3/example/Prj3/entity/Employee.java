package prj3.example.Prj3.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import prj3.example.Prj3.enums.ActivationStatus;
import prj3.example.Prj3.enums.PersonType;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "employees")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 12)
    private String cccd; 

    @Column(name = "employee_code", unique = true, length = 30)
    private String employeeCode; 

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private LocalDate birthday;
    private String gender;

    @Column(name = "image_raw_url", columnDefinition = "LONGTEXT")
    private String imageRawUrl; 

    @Column(name = "face_vector", columnDefinition = "JSON")
    private String faceVector; 
    
    @Column(name = "backup_photo_embedding", columnDefinition = "JSON")
    private String backupPhotoEmbedding; 
    
    private String department;  
    
    private String position;  
    
    @Column(length = 100)
    private String email;
    
    @Column(length = 20)
    private String phone;
    
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    

    @Enumerated(EnumType.STRING)
    @Column(name = "person_type", length = 20, nullable = false)
    @Builder.Default
    private PersonType personType = PersonType.EMPLOYEE;

    

    @Enumerated(EnumType.STRING)
    @Column(name = "activation_status", length = 25, nullable = false)
    @Builder.Default
    private ActivationStatus activationStatus = ActivationStatus.PENDING_ACTIVATION;

    @Column(name = "first_registered_at")
    private LocalDateTime firstRegisteredAt; 

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @ManyToOne
    @JoinColumn(name = "organization_id")
    private Organization organization; 
}