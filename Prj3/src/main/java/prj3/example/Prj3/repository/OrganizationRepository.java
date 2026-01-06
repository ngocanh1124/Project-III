package prj3.example.Prj3.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import prj3.example.Prj3.model.Organization;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long> {
}