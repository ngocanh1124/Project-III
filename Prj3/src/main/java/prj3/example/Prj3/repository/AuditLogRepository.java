package prj3.example.Prj3.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import prj3.example.Prj3.entity.AuditLog;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByActorOrderByCreatedAtDesc(String actor, Pageable pageable);

    Page<AuditLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);

    Page<AuditLog> findByResourceTypeAndResourceIdOrderByCreatedAtDesc(
            String resourceType, String resourceId, Pageable pageable);

    List<AuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime from, LocalDateTime to);

    Page<AuditLog> findByOrganizationIdOrderByCreatedAtDesc(Long orgId, Pageable pageable);
}
