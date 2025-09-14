package backend.consutalar_correo.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class NetflixLinkResponse {

    private boolean success;
    private String message;
    private String netflixLink;
    private String temporaryCode;
    private String email;

    // Constructor para éxito con enlace
    public NetflixLinkResponse(boolean success, String netflixLink, String email) {
        this.success = success;
        this.netflixLink = netflixLink;
        this.email = email;
        this.message = success ? "Se encontro enlace de actualizar hogar" : null;
    }

    // Constructor para éxito con código temporal
    public NetflixLinkResponse(boolean success, String data, String email, boolean isCode) {
        this.success = success;
        this.email = email;
        if (isCode) {
            this.temporaryCode = data;
            this.message = "Se encontro codigo temporal";
        } else {
            this.netflixLink = data;
            this.message = "Se encontro enlace de actualizar hogar";
        }
    }

    // Constructor para error
    public NetflixLinkResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    // Getters y Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getNetflixLink() { return netflixLink; }
    public void setNetflixLink(String netflixLink) { this.netflixLink = netflixLink; }

    public String getTemporaryCode() { return temporaryCode; }
    public void setTemporaryCode(String temporaryCode) { this.temporaryCode = temporaryCode; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}