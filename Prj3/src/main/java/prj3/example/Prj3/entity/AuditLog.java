package prj3.example.Prj3.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_actor",    columnList = "actor"),
        @Index(name = "idx_audit_action",   columnList = "action"),
        @Index(name = "idx_audit_resource", columnList = "resource_type,resource_id"),
        @Index(name = "idx_audit_time",     columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    
    @Column(nullable = false, length = 100)
    private String actor;

    

    @Column(nullable = false, length = 60)
    private String action;

    
    @Column(name = "resource_type", length = 50)
    private String resourceType;

    
    @Column(name = "resource_id", length = 100)
    private String resourceId;

    
    @Column(nullable = false, length = 20)
    private String outcome;

    
    @Column(columnDefinition = "TEXT")
    private String detail;

    
    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    
    @Column(name = "user_agent", length = 200)
    private String userAgent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = true)
    private Organization organization;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
