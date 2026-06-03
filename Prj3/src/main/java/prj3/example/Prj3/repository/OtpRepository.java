package prj3.example.Prj3.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import prj3.example.Prj3.entity.VerificationOtp;
import prj3.example.Prj3.entity.User;
import java.util.Optional;

@Repository
public interface OtpRepository extends JpaRepository<VerificationOtp, Long> {
    
    Optional<VerificationOtp> findByUserAndOtpCodeAndIsUsedFalse(User user, String otpCode);
}