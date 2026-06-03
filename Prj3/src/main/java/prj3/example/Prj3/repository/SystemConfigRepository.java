package prj3.example.Prj3.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import prj3.example.Prj3.entity.SystemConfig;
import java.util.Optional;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {
    Optional<SystemConfig> findByConfigKey(String key);
}