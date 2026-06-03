package prj3.example.Prj3.entity;

import jakarta.persistence.*;
import lombok.Data;
import prj3.example.Prj3.enums.OtpType;
import java.time.LocalDateTime;

@Entity
@Table(name = "verification_otps")
@Data
public class VerificationOtp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 6)
    private String otpCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OtpType type;

    @Column(nullable = false)
    private LocalDateTime expiryTime;

    private Boolean isUsed = false;
    private LocalDateTime createdAt = LocalDateTime.now();
}