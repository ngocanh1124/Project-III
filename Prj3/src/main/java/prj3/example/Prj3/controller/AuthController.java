package prj3.example.Prj3.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import prj3.example.Prj3.dto.UserRegistrationDTO;
import prj3.example.Prj3.service.AuthService;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired private AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserRegistrationDTO dto) {
        try {
            String otp = authService.register(dto);
            if (otp != null) {
                
                return ResponseEntity.ok(Map.of("message", "Ma OTP da duoc gui!", "dev_otp", otp));
            }
            return ResponseEntity.ok(Map.of("message", "Ma OTP da duoc gui dden email cua ban!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> request) {
        boolean ok = authService.verifyOtp(request.get("email"), request.get("otpCode"));
        return ok ? ResponseEntity.ok(Map.of("message", "Xac thuc thanh cong!"))
                  : ResponseEntity.badRequest().body(Map.of("message", "OTP sai hoac het han."));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        try {
            return ResponseEntity.ok(authService.login(request.get("username"), request.get("password")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        try {
            authService.forgotPassword(request.get("email"));
            return ResponseEntity.ok(Map.of("message", "OTP dat lai mat khau da gui!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> request) {
        try {
            authService.changePassword(request.get("email"), request.get("otpCode"), request.get("newPassword"));
            return ResponseEntity.ok(Map.of("message", "Mat khau da doi thanh cong!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile() {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            return ResponseEntity.ok(authService.getProfile(username));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, String> request) {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            return ResponseEntity.ok(authService.updateProfile(username, request.get("fullName"), request.get("email")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    
    @PostMapping("/change-my-password")
    public ResponseEntity<?> changeMyPassword(@RequestBody Map<String, String> request) {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            authService.changeMyPassword(username, request.get("currentPassword"), request.get("newPassword"));
            return ResponseEntity.ok(Map.of("message", "Mật khẩu đã đổi thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    
    @GetMapping("/validate")
    public ResponseEntity<?> validateToken() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("valid", false, "message", "Token không hợp lệ hoặc đã hết hạn"));
        }
        return ResponseEntity.ok(Map.of("valid", true, "username", auth.getName()));
    }
}
