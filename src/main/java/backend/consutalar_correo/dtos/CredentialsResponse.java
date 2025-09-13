package backend.consutalar_correo.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CredentialsResponse {

    private Long id;
    private String email;
    private String provider;
    private String imapHost;
    private Integer imapPort;
    private Boolean sslEnabled;

    public CredentialsResponse() {}

    public CredentialsResponse(Long id, String email, String provider, String imapHost, Integer imapPort, Boolean sslEnabled) {
        this.id = id;
        this.email = email;
        this.provider = provider;
        this.imapHost = imapHost;
        this.imapPort = imapPort;
        this.sslEnabled = sslEnabled;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getImapHost() {
        return imapHost;
    }

    public void setImapHost(String imapHost) {
        this.imapHost = imapHost;
    }

    public Integer getImapPort() {
        return imapPort;
    }

    public void setImapPort(Integer imapPort) {
        this.imapPort = imapPort;
    }

    public Boolean getSslEnabled() {
        return sslEnabled;
    }

    public void setSslEnabled(Boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }
}
