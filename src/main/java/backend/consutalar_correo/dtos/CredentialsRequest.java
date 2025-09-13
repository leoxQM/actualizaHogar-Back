package backend.consutalar_correo.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class CredentialsRequest {

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El formato del email no es válido")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    private String password;

    @NotBlank(message = "El proveedor es obligatorio")
    @Pattern(regexp = "^(GMAIL|OUTLOOK|YAHOO)$",
            message = "El proveedor debe ser: GMAIL, OUTLOOK o YAHOO")
    private String provider;

    public CredentialsRequest() {}

    public CredentialsRequest(String email, String password, String provider) {
        this.email = email;
        this.password = password;
        this.provider = provider;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }
}
