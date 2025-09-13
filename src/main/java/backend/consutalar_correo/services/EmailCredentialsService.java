package backend.consutalar_correo.services;

import backend.consutalar_correo.entities.EmailCredentials;

import java.util.List;
import java.util.Optional;

public interface EmailCredentialsService {

    EmailCredentials saveCredentials(String email, String password, String provider);

    Optional<EmailCredentials> getCredentialsByEmail(String email);

    List<EmailCredentials> getAllCredentials();

    boolean deleteCredentials(String email);

    boolean existsByEmail(String email);

    boolean validateCredentials(String email);
}
