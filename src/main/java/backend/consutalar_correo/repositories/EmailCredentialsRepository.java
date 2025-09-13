package backend.consutalar_correo.repositories;

import backend.consutalar_correo.entities.EmailCredentials;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailCredentialsRepository extends JpaRepository<EmailCredentials, Long> {

    Optional<EmailCredentials> findByEmail(String email);

    boolean existsByEmail(String email);
}
