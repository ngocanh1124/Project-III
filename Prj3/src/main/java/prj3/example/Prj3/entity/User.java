package prj3.example.Prj3.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    private String fullName;

    @Column(unique = true, nullable = false)
    private String email;

    private Boolean isVerified = false;
    private Boolean isActive = true;
    private LocalDateTime createdAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.VIEWER;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    

    public enum UserRole {
        VIEWER,
        HR_MANAGER,
        OPERATOR,
        ADMIN,
        SUPER_ADMIN
    }
}