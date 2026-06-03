package prj3.example.Prj3.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import prj3.example.Prj3.entity.User;
import prj3.example.Prj3.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class AdminUserController {

    @Autowired private UserRepository userRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    
    @GetMapping
    public ResponseEntity<?> listUsers() {
        List<Map<String, Object>> users = userRepository.findAll().stream()
                .map(this::toSafeMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    
    @GetMapping("/{id}")
    public ResponseEntity<?> getUser(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(u -> ResponseEntity.ok(toSafeMap(u)))
                .orElse(ResponseEntity.notFound().build());
    }

    
    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String email    = body.get("email");
        String password = body.get("password");
        String roleStr  = body.getOrDefault("role", "VIEWER");
        String fullName = body.getOrDefault("fullName", "");

        if (username == null || email == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "username, email, password bắt buộc"));
        }
        if (userRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username đã tồn tại"));
        }
        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email đã tồn tại"));
        }

        User.UserRole role;
        try { role = User.UserRole.valueOf(roleStr.toUpperCase()); }
        catch (IllegalArgumentException e) { role = User.UserRole.VIEWER; }

        User.UserRole callerRole = getCallerRole();
        if (role.ordinal() >= callerRole.ordinal()) {
            return ResponseEntity.status(403).body(Map.of("error",
                "Không thể tạo tài khoản với quyền bằng hoặc cao hơn quyền của bạn (" + callerRole + ")"));
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setFullName(fullName);
        user.setRole(role);
        user.setIsVerified(true);
        user.setIsActive(true);
        user.setCreatedAt(LocalDateTime.now());

        User saved = userRepository.save(user);
        return ResponseEntity.ok(toSafeMap(saved));
    }

    
    @PutMapping("/{id}/role")
    public ResponseEntity<?> updateRole(@PathVariable Long id,
                                        @RequestBody Map<String, String> body) {
        User.UserRole callerRole = getCallerRole();
        return userRepository.findById(id).map(user -> {
            if (user.getRole().ordinal() >= callerRole.ordinal()) {
                return ResponseEntity.status(403).body(Map.of("error",
                    "Không thể thay đổi quyền của người dùng có role bằng hoặc cao hơn bạn"));
            }
            String roleStr = body.get("role");
            User.UserRole newRole;
            try {
                newRole = User.UserRole.valueOf(roleStr.toUpperCase());
            } catch (Exception e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Role không hợp lệ: " + roleStr));
            }
            if (newRole.ordinal() >= callerRole.ordinal()) {
                return ResponseEntity.status(403).body(Map.of("error",
                    "Không thể gán quyền bằng hoặc cao hơn quyền của bạn (" + callerRole + ")"));
            }
            user.setRole(newRole);
            userRepository.save(user);
            return ResponseEntity.ok(toSafeMap(user));
        }).orElse(ResponseEntity.notFound().build());
    }

    
    @PutMapping("/{id}/toggle-active")
    public ResponseEntity<?> toggleActive(@PathVariable Long id) {
        User.UserRole callerRole = getCallerRole();
        return userRepository.findById(id).map(user -> {
            if (user.getRole().ordinal() >= callerRole.ordinal()) {
                return ResponseEntity.status(403).body(Map.of("error",
                    "Không thể khóa/mở tài khoản có role bằng hoặc cao hơn bạn"));
            }
            user.setIsActive(!Boolean.TRUE.equals(user.getIsActive()));
            userRepository.save(user);
            return ResponseEntity.ok(toSafeMap(user));
        }).orElse(ResponseEntity.notFound().build());
    }

    
    @PutMapping("/{id}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable Long id,
                                           @RequestBody Map<String, String> body) {
        String newPw = body.get("password");
        if (newPw == null || newPw.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "Mật khẩu tối thiểu 6 ký tự"));
        }
        return userRepository.findById(id).map(user -> {
            user.setPassword(passwordEncoder.encode(newPw));
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("message", "Đã đặt lại mật khẩu"));
        }).orElse(ResponseEntity.notFound().build());
    }

    
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        User.UserRole callerRole = getCallerRole();
        return userRepository.findById(id).map(user -> {
            if (user.getRole().ordinal() >= callerRole.ordinal()) {
                return ResponseEntity.status(403).body(Map.of("error",
                    "Không thể xóa tài khoản có role bằng hoặc cao hơn bạn"));
            }
            userRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "Đã xóa tài khoản"));
        }).orElse(ResponseEntity.notFound().build());
    }

    
    private User.UserRole getCallerRole() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .map(User::getRole)
                .orElse(User.UserRole.VIEWER);
    }

    
    private Map<String, Object> toSafeMap(User u) {
        return Map.of(
                "id",         u.getId(),
                "username",   u.getUsername(),
                "email",      u.getEmail(),
                "fullName",   u.getFullName() != null ? u.getFullName() : "",
                "role",       u.getRole().name(),
                "isActive",   Boolean.TRUE.equals(u.getIsActive()),
                "isVerified", Boolean.TRUE.equals(u.getIsVerified()),
                "createdAt",  u.getCreatedAt() != null ? u.getCreatedAt().toString() : ""
        );
    }
}
