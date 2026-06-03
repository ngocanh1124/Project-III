package prj3.example.Prj3.repository;

import prj3.example.Prj3.entity.Device;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {
    Optional<Device> findByDeviceCode(String deviceCode);

    Optional<Device> findByDeviceCodeIgnoreCase(String deviceCode);

    Page<Device> findByDeviceCodeContainingOrLocationNameContaining(String deviceCode, String locationName, Pageable pageable);
}