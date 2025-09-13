package backend.consutalar_correo.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "email_credentials")
public class EmailCredentials {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    @Email
    @NotBlank
    private String email;

    @Column(nullable = false)
    @NotBlank
    private String encryptedPassword;

    @Column(nullable = false)
    @NotBlank
    private String provider; // GMAIL, OUTLOOK, YAHOO, etc.

    @Column(nullable = false)
    private String imapHost;

    @Column(nullable = false)
    private Integer imapPort;

    @Column(nullable = false)
    private Boolean sslEnabled;

    // Constructores
    public EmailCredentials() {}

    public EmailCredentials(String email, String encryptedPassword, String provider) {
        this.email = email;
        this.encryptedPassword = encryptedPassword;
        this.provider = provider;
        setProviderDefaults();
    }

    // Método para establecer configuraciones por defecto según el proveedor
    private void setProviderDefaults() {
        switch (provider.toUpperCase()) {
            case "GMAIL":
                this.imapHost = "imap.gmail.com";
                this.imapPort = 993;
                this.sslEnabled = true;
                break;
            case "OUTLOOK":
                this.imapHost = "outlook.office365.com";
                this.imapPort = 993;
                this.sslEnabled = true;
                break;
            case "YAHOO":
                this.imapHost = "imap.mail.yahoo.com";
                this.imapPort = 993;
                this.sslEnabled = true;
                break;
            default:
                throw new IllegalArgumentException("Proveedor no soportado: " + provider);
        }
    }

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getEncryptedPassword() { return encryptedPassword; }
    public void setEncryptedPassword(String encryptedPassword) { this.encryptedPassword = encryptedPassword; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) {
        this.provider = provider;
        setProviderDefaults();
    }

    public String getImapHost() { return imapHost; }
    public void setImapHost(String imapHost) { this.imapHost = imapHost; }

    public Integer getImapPort() { return imapPort; }
    public void setImapPort(Integer imapPort) { this.imapPort = imapPort; }

    public Boolean getSslEnabled() { return sslEnabled; }
    public void setSslEnabled(Boolean sslEnabled) { this.sslEnabled = sslEnabled; }
}
