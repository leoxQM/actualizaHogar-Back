package backend.consutalar_correo.controllers;

import backend.consutalar_correo.services.CorreoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/netflix/central")
@CrossOrigin(origins = "*")
public class CorreoControllers {
    private final CorreoService service;

    // ✅ El constructor debe tener el mismo nombre que la clase
    public CorreoControllers(CorreoService service) {
        this.service = service;
    }

    @PostMapping("/actualizar-hogar")
    public ResponseEntity<String> actualizarHogar(@RequestBody String correo) {
        String enlace = service.findNetflixUpdateHomeLinkFor(correo);
        if (enlace != null) {
            return ResponseEntity.ok(enlace);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No se encontró enlace para el correo ingresado.");
        }
    }
}
