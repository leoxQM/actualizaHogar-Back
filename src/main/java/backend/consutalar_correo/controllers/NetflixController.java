package backend.consutalar_correo.controllers;

import backend.consutalar_correo.dtos.EmailRequest;
import backend.consutalar_correo.dtos.NetflixLinkResponse;
import backend.consutalar_correo.services.EmailProcessorService;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/netflix")
//@CrossOrigin(origins = "http://localhost:4200")
public class NetflixController {
    private static final Logger logger = LoggerFactory.getLogger(NetflixController.class);

    @Autowired
    private EmailProcessorService emailProcessorService;

    @RateLimiter(name = "netflix-service", fallbackMethod = "rateLimitFallback")
    @PostMapping("/extract-link")
    public ResponseEntity<NetflixLinkResponse> extractNetflixLink(@Valid @RequestBody EmailRequest request) {
        logger.info("Solicitud para extraer enlace de Netflix del email: {}", request.getEmail());

        try {
            Optional<String> netflixLink = emailProcessorService.extractNetflixHomeLink(request.getEmail());

            if (netflixLink.isPresent()) {
                logger.info("Enlace extraído exitosamente para: {}", request.getEmail());
                return ResponseEntity.ok(
                        new NetflixLinkResponse(true, netflixLink.get(), request.getEmail())
                );
            } else {
                logger.warn("No se encontró enlace de Netflix para: {}", request.getEmail());
                return ResponseEntity.ok(
                        new NetflixLinkResponse(false, "No se encontró enlace de actualización de hogar.")
                );
            }

        } catch (Exception e) {
            logger.error("Error procesando solicitud para {}: {}", request.getEmail(), e.getMessage());
            return ResponseEntity.status(500).body(
                    new NetflixLinkResponse(false, "Error interno del servidor: " + e.getMessage())
            );
        }
    }

    @RateLimiter(name = "netflix-service", fallbackMethod = "rateLimitFallbackCode")
    @PostMapping("/extract-code")
    public ResponseEntity<NetflixLinkResponse> extractTemporaryCode(@Valid @RequestBody EmailRequest request) {
        logger.info("Solicitud para extraer código temporal del email: {}", request.getEmail());

        try {
            Optional<String> temporaryCode = emailProcessorService.extractTemporaryCode(request.getEmail());

            if (temporaryCode.isPresent()) {
                logger.info("Código temporal extraído exitosamente para: {}", request.getEmail());
                NetflixLinkResponse response = new NetflixLinkResponse(true, temporaryCode.get(), request.getEmail(), true);
                return ResponseEntity.ok(response);
            } else {
                logger.warn("No se encontró código temporal para: {}", request.getEmail());
                return ResponseEntity.ok(
                        new NetflixLinkResponse(false, "No se encontró código temporal")
                );
            }

        } catch (Exception e) {
            logger.error("Error procesando solicitud de código temporal para {}: {}", request.getEmail(), e.getMessage());
            return ResponseEntity.status(500).body(
                    new NetflixLinkResponse(false, "Error interno del servidor: " + e.getMessage())
            );
        }
    }

    @RateLimiter(name = "netflix-validation", fallbackMethod = "rateLimitFallbackValidation")
    @PostMapping("/validate-connection")
    public ResponseEntity<NetflixLinkResponse> validateConnection(@Valid @RequestBody EmailRequest request) {
        logger.info("Validando conexión para email: {}", request.getEmail());

        try {
            boolean isValid = emailProcessorService.validateEmailConnection(request.getEmail());

            if (isValid) {
                return ResponseEntity.ok(
                        new NetflixLinkResponse(true, "Conexión válida")
                );
            } else {
                return ResponseEntity.ok(
                        new NetflixLinkResponse(false, "No se puede conectar al email o no existen credenciales")
                );
            }

        } catch (Exception e) {
            logger.error("Error validando conexión para {}: {}", request.getEmail(), e.getMessage());
            return ResponseEntity.status(500).body(
                    new NetflixLinkResponse(false, "Error validando conexión: " + e.getMessage())
            );
        }
    }

    // Fallback methods para rate limiting
    public ResponseEntity<NetflixLinkResponse> rateLimitFallback(EmailRequest request, RequestNotPermitted ex) {
        logger.warn("Rate limit excedido para extraer enlace: {}", request.getEmail());
        return ResponseEntity.status(429).body(
                new NetflixLinkResponse(false, "Límite de solicitudes excedido. Máximo 5 por minuto. Intenta nuevamente en unos minutos.")
        );
    }

    public ResponseEntity<NetflixLinkResponse> rateLimitFallbackCode(EmailRequest request, RequestNotPermitted ex) {
        logger.warn("Rate limit excedido para extraer código: {}", request.getEmail());
        return ResponseEntity.status(429).body(
                new NetflixLinkResponse(false, "Límite de solicitudes excedido. Máximo 5 por minuto. Intenta nuevamente en unos minutos.")
        );
    }

    public ResponseEntity<NetflixLinkResponse> rateLimitFallbackValidation(EmailRequest request, RequestNotPermitted ex) {
        logger.warn("Rate limit excedido para validación: {}", request.getEmail());
        return ResponseEntity.status(429).body(
                new NetflixLinkResponse(false, "Límite de validaciones excedido. Máximo 10 por minuto. Intenta nuevamente en unos minutos.")
        );
    }
}