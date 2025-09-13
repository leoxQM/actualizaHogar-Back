package backend.consutalar_correo.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "mail.imap")
public class ImapProperties {
    private String host;
    private int port;
    private boolean ssl;
    private String username;
    private String password;
    private String folder;
    private int lastMessagesToScan = 50;

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public boolean isSsl() { return ssl; }
    public void setSsl(boolean ssl) { this.ssl = ssl; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getFolder() { return folder; }
    public void setFolder(String folder) { this.folder = folder; }
    public int getLastMessagesToScan() { return lastMessagesToScan; }
    public void setLastMessagesToScan(int lastMessagesToScan) { this.lastMessagesToScan = lastMessagesToScan; }
}
