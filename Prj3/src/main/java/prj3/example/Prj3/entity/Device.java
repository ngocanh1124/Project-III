package prj3.example.Prj3.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "devices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_code", unique = true, nullable = false, length = 50)
    private String deviceCode; 

    @Column(name = "location_name", nullable = false, length = 100)
    private String locationName; 
    
    @Column(name = "location", length = 100)
    private String location; 
    
    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "is_online")
    @Builder.Default
    private Boolean isOnline = false;

    @Column(name = "last_sync")
    @Builder.Default
    private LocalDateTime lastSync = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;
}