package prj3.example.Prj3.repository;
import org.springframework.data.jpa.repository.JpaRepository;

import prj3.example.Prj3.model.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    AppUser findByUsername(String username);
}