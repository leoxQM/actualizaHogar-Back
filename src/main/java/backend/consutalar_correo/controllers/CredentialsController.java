package backend.consutalar_correo.controllers;

import backend.consutalar_correo.dtos.CredentialsRequest;
import backend.consutalar_correo.dtos.CredentialsResponse;
import backend.consutalar_correo.entities.EmailCredentials;
import backend.consutalar_correo.services.EmailCredentialsService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/credentials")
public class CredentialsController {

    private static final Logger logger = LoggerFactory.getLogger(CredentialsController.class);

    @Autowired
    private EmailCredentialsService emailCredentialsService;

    @PostMapping
    public ResponseEntity<?> saveCredentials(@Valid @RequestBody CredentialsRequest request) {
        logger.info("Guardando credenciales para email: {}", request.getEmail());

        try {
            EmailCredentials savedCredentials = emailCredentialsService.saveCredentials(
                    request.getEmail(),
                    request.getPassword(),
                    request.getProvider()
            );

            CredentialsResponse response = mapToResponse(savedCredentials);

            logger.info("Credenciales guardadas exitosamente para: {}", request.getEmail());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            logger.error("Error guardando credenciales para {}: {}", request.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error guardando credenciales: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<CredentialsResponse>> getAllCredentials() {
        logger.info("Obteniendo todas las credenciales");

        try {
            List<EmailCredentials> credentials = emailCredentialsService.getAllCredentials();
            List<CredentialsResponse> responses = credentials.stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(responses);

        } catch (Exception e) {
            logger.error("Error obteniendo credenciales: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{email}")
    public ResponseEntity<?> getCredentialsByEmail(@PathVariable String email) {
        logger.info("Obteniendo credenciales para email: {}", email);

        try {
            Optional<EmailCredentials> credentials = emailCredentialsService.getCredentialsByEmail(email);

            if (credentials.isPresent()) {
                CredentialsResponse response = mapToResponse(credentials.get());
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("No se encontraron credenciales para el email: " + email);
            }

        } catch (Exception e) {
            logger.error("Error obteniendo credenciales para {}: {}", email, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error obteniendo credenciales: " + e.getMessage());
        }
    }

    @DeleteMapping("/{email}")
    public ResponseEntity<?> deleteCredentials(@PathVariable String email) {
        logger.info("Eliminando credenciales para email: {}", email);

        try {
            boolean deleted = emailCredentialsService.deleteCredentials(email);

            if (deleted) {
                return ResponseEntity.ok("Credenciales eliminadas exitosamente");
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("No se encontraron credenciales para eliminar: " + email);
            }

        } catch (Exception e) {
            logger.error("Error eliminando credenciales para {}: {}", email, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error eliminando credenciales: " + e.getMessage());
        }
    }

    @GetMapping("/exists/{email}")
    public ResponseEntity<Boolean> checkCredentialsExist(@PathVariable String email) {
        logger.info("Verificando existencia de credenciales para: {}", email);

        try {
            boolean exists = emailCredentialsService.existsByEmail(email);
            return ResponseEntity.ok(exists);

        } catch (Exception e) {
            logger.error("Error verificando existencia de credenciales para {}: {}", email, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/validate/{email}")
    public ResponseEntity<?> validateCredentials(@PathVariable String email) {
        logger.info("Validando credenciales para: {}", email);

        try {
            boolean isValid = emailCredentialsService.validateCredentials(email);

            if (isValid) {
                return ResponseEntity.ok("Credenciales válidas");
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Credenciales inválidas o no encontradas");
            }

        } catch (Exception e) {
            logger.error("Error validando credenciales para {}: {}", email, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error validando credenciales: " + e.getMessage());
        }
    }

    private CredentialsResponse mapToResponse(EmailCredentials credentials) {
        return new CredentialsResponse(
                credentials.getId(),
                credentials.getEmail(),
                credentials.getProvider(),
                credentials.getImapHost(),
                credentials.getImapPort(),
                credentials.getSslEnabled()
        );
    }
}
