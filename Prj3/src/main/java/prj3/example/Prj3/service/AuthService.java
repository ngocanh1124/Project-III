package prj3.example.Prj3.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import prj3.example.Prj3.config.JwtUtil;
import prj3.example.Prj3.dto.UserRegistrationDTO;
import prj3.example.Prj3.entity.User;
import prj3.example.Prj3.entity.VerificationOtp;
import prj3.example.Prj3.enums.OtpType;
import prj3.example.Prj3.repository.OtpRepository;
import prj3.example.Prj3.repository.UserRepository;
import prj3.example.Prj3.repository.OrganizationRepository;
import prj3.example.Prj3.entity.Organization;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpRepository otpRepository;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JwtUtil jwtUtil;

    public String register(UserRegistrationDTO dto) {
        
        if (userRepository.findByUsername(dto.getUsername()).isPresent()) {
            throw new RuntimeException("Tên đăng nhập đã tồn tại");
        }
        if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new RuntimeException("Email đã được đăng ký. Vui lòng đăng nhập hoặc dùng email khác");
        }

        
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setIsVerified(false);
        
        if (dto.getOrganizationCode() != null && !dto.getOrganizationCode().isBlank()) {
            organizationRepository.findByCode(dto.getOrganizationCode())
                    .ifPresent(user::setOrganization);
        }
        userRepository.save(user);

        
        String otpStr = String.valueOf(new Random().nextInt(899999) + 100000);
        
        VerificationOtp otp = new VerificationOtp();
        otp.setUser(user);
        otp.setOtpCode(otpStr);
        otp.setExpiryTime(LocalDateTime.now().plusMinutes(5)); 
        otp.setType(OtpType.REGISTRATION);
        otpRepository.save(otp);

        
        try {
            sendEmail(user.getEmail(), otpStr);
            return null; 
        } catch (Exception e) {
            System.err.println("[WARN] Không gửi được mail: " + e.getMessage());
            System.out.println("[DEV] OTP cho " + user.getEmail() + " là: " + otpStr);
            return otpStr; 
        }
    }

    private void sendEmail(String email, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("nguyenthingocanh2004pt@gmail.com");
        message.setTo(email);
        message.setSubject("Mã xác thực hệ thống điểm danh CCCD");
        message.setText("Mã OTP của bạn là: " + otp + "\nVui lòng không chia sẻ mã này.");
        mailSender.send(message);
    }
    
    public boolean verifyOtp(String email, String otpCode) {
        
        java.util.Optional<User> userOpt = userRepository.findByEmail(email);
        if (!userOpt.isPresent()) {
            return false;
        }
        
        User user = userOpt.get();
        
        
        java.util.Optional<VerificationOtp> otpOpt = otpRepository.findByUserAndOtpCodeAndIsUsedFalse(user, otpCode);
        if (!otpOpt.isPresent()) {
            return false;
        }
        
        VerificationOtp otp = otpOpt.get();
        
        
        if (otp.getExpiryTime().isBefore(LocalDateTime.now())) {
            return false; 
        }
        
        
        user.setIsVerified(true);
        userRepository.save(user);
        
        
        otp.setIsUsed(true);
        otpRepository.save(otp);
        
        return true;
    }

    
    public Map<String, Object> login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại"));

        if (!user.getIsVerified()) {
            throw new RuntimeException("Tài khoản chưa được xác thực. Vui lòng xác thực OTP.");
        }

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new RuntimeException("Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Sai mật khẩu");
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole().toString());

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("email", user.getEmail());
        userInfo.put("fullName", user.getFullName() != null ? user.getFullName() : user.getUsername());
        userInfo.put("role", user.getRole().toString());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", userInfo);
        return result;
    }

    
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email không tồn tại trong hệ thống"));

        String otpStr = String.valueOf(new Random().nextInt(899999) + 100000);

        VerificationOtp otp = new VerificationOtp();
        otp.setUser(user);
        otp.setOtpCode(otpStr);
        otp.setExpiryTime(LocalDateTime.now().plusMinutes(10));
        otp.setType(OtpType.FORGOT_PASSWORD);
        otpRepository.save(otp);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("nguyenthingocanh2004pt@gmail.com");
            message.setTo(email);
            message.setSubject("Đặt lại mật khẩu - Hệ thống điểm danh CCCD");
            message.setText("Mã OTP đặt lại mật khẩu của bạn là: " + otpStr + "\nMã có hiệu lực trong 10 phút.");
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("[WARN] Không gửi được mail: " + e.getMessage());
            System.out.println("[DEV] OTP quên mật khẩu cho " + email + " là: " + otpStr);
        }
    }

    
    public void changePassword(String email, String otpCode, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email không tồn tại"));

        VerificationOtp otp = otpRepository.findByUserAndOtpCodeAndIsUsedFalse(user, otpCode)
                .orElseThrow(() -> new RuntimeException("OTP không đúng hoặc đã sử dụng"));

        if (otp.getExpiryTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("OTP đã hết hạn");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        otp.setIsUsed(true);
        otpRepository.save(otp);
    }

    
    public void changeMyPassword(String username, String currentPassword, String newPassword) {
        if (currentPassword == null || newPassword == null || newPassword.length() < 6) {
            throw new RuntimeException("Mật khẩu mới phải có ít nhất 6 ký tự");
        }
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản"));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new RuntimeException("Mật khẩu hiện tại không đúng");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    
    public Map<String, Object> getProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản"));

        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getId());
        profile.put("username", user.getUsername());
        profile.put("email", user.getEmail());
        profile.put("fullName", user.getFullName() != null ? user.getFullName() : "");
        profile.put("role", user.getRole().toString());
        return profile;
    }

    
    public Map<String, Object> updateProfile(String username, String fullName, String email) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản"));

        if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName);
        }
        if (email != null && !email.isBlank()) {
            
            userRepository.findByEmail(email).ifPresent(existing -> {
                if (!existing.getId().equals(user.getId())) {
                    throw new RuntimeException("Email đã được sử dụng bởi tài khoản khác");
                }
            });
            user.setEmail(email);
        }
        userRepository.save(user);

        return getProfile(username);
    }
}
