package backend.consutalar_correo.services.serviceImpl;

import backend.consutalar_correo.entities.EmailCredentials;
import backend.consutalar_correo.repositories.EmailCredentialsRepository;
import backend.consutalar_correo.services.EmailCredentialsService;
import backend.consutalar_correo.services.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EmailCredentialsServiceImpl implements EmailCredentialsService {

    private static final Logger logger = LoggerFactory.getLogger(EmailCredentialsServiceImpl.class);

    @Autowired
    private EmailCredentialsRepository repository;

    @Autowired
    private EncryptionService encryptionService;

    @Override
    public EmailCredentials saveCredentials(String email, String password, String provider) {
        logger.info("Guardando credenciales para email: {}", email);

        // Verificar si ya existen credenciales para este email
        Optional<EmailCredentials> existingCredentials = repository.findByEmail(email);

        EmailCredentials credentials;
        if (existingCredentials.isPresent()) {
            // Actualizar credenciales existentes
            credentials = existingCredentials.get();
            credentials.setEncryptedPassword(encryptionService.encrypt(password));
            credentials.setProvider(provider);
            logger.info("Actualizando credenciales existentes para: {}", email);
        } else {
            // Crear nuevas credenciales
            credentials = new EmailCredentials(email, encryptionService.encrypt(password), provider);
            logger.info("Creando nuevas credenciales para: {}", email);
        }

        return repository.save(credentials);
    }

    @Override
    public Optional<EmailCredentials> getCredentialsByEmail(String email) {
        return repository.findByEmail(email);
    }

    @Override
    public List<EmailCredentials> getAllCredentials() {
        return repository.findAll();
    }

    @Override
    public boolean deleteCredentials(String email) {
        Optional<EmailCredentials> credentials = repository.findByEmail(email);
        if (credentials.isPresent()) {
            repository.delete(credentials.get());
            logger.info("Credenciales eliminadas para: {}", email);
            return true;
        }
        return false;
    }

    @Override
    public boolean existsByEmail(String email) {
        return repository.existsByEmail(email);
    }

    @Override
    public boolean validateCredentials(String email) {
        try {
            Optional<EmailCredentials> credentialsOpt = getCredentialsByEmail(email);
            if (credentialsOpt.isEmpty()) {
                return false;
            }

            EmailCredentials credentials = credentialsOpt.get();
            String decryptedPassword = encryptionService.decrypt(credentials.getEncryptedPassword());

            // Aquí podrías agregar lógica para verificar la conexión
            // Por ahora solo verificamos que se pueda descifrar
            return decryptedPassword != null && !decryptedPassword.isEmpty();

        } catch (Exception e) {
            logger.error("Error validando credenciales para {}: {}", email, e.getMessage());
            return false;
        }
    }
}
