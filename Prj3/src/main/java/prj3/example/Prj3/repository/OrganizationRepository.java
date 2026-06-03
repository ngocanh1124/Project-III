package prj3.example.Prj3.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import prj3.example.Prj3.entity.Organization;

import java.util.Optional;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Optional<Organization> findByCode(String code);
    Optional<Organization> findByName(String name);
    Optional<Organization> findByEmail(String email);
}
