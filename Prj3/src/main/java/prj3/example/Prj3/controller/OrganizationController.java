package prj3.example.Prj3.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import prj3.example.Prj3.entity.Organization;
import prj3.example.Prj3.repository.OrganizationRepository;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/organizations")
public class OrganizationController {

    @Autowired
    private OrganizationRepository organizationRepository;

    @GetMapping
    public ResponseEntity<?> getAll() {
        return ResponseEntity.ok(organizationRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return organizationRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> body) {
        try {
            if (body.get("name") == null || body.get("code") == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Tên và mã tổ chức là bắt buộc"));
            }
            if (organizationRepository.findByCode(body.get("code")).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Mã tổ chức đã tồn tại"));
            }
            Organization org = new Organization();
            org.setName(body.get("name"));
            org.setCode(body.get("code"));
            org.setDescription(body.get("description"));
            org.setPhone(body.get("phone"));
            org.setEmail(body.get("email"));
            org.setAddress(body.get("address"));
            org.setIsActive(true);
            org.setCreatedAt(LocalDateTime.now());
            return ResponseEntity.ok(organizationRepository.save(org));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return organizationRepository.findById(id).map(org -> {
            if (body.containsKey("name")) org.setName(body.get("name"));
            if (body.containsKey("description")) org.setDescription(body.get("description"));
            if (body.containsKey("phone")) org.setPhone(body.get("phone"));
            if (body.containsKey("email")) org.setEmail(body.get("email"));
            if (body.containsKey("address")) org.setAddress(body.get("address"));
            org.setUpdatedAt(LocalDateTime.now());
            return ResponseEntity.ok(organizationRepository.save(org));
        }).orElse(ResponseEntity.notFound().build());
    }
}
