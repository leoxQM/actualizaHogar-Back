package backend.consutalar_correo.services.serviceImpl;

import backend.consutalar_correo.entities.EmailCredentials;
import backend.consutalar_correo.repositories.EmailCredentialsRepository;
import backend.consutalar_correo.services.EmailProcessorService;
import backend.consutalar_correo.services.EncryptionService;

import jakarta.mail.*;
import jakarta.mail.search.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Calendar;
import java.util.Date;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EmailProcessorServiceImpl implements EmailProcessorService {

    private static final Logger logger = LoggerFactory.getLogger(EmailProcessorServiceImpl.class);

    @Autowired
    private EmailCredentialsRepository emailCredentialsRepository;

    @Autowired
    private EncryptionService encryptionService;

    // HTTP Client reutilizable para evitar crear múltiples instancias
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // OPTIMIZACIÓN: Cache para credenciales (reduce consultas DB)
    @Cacheable(value = "emailCredentials", key = "#email")
    private Optional<EmailCredentials> getCachedCredentials(String email) {
        return emailCredentialsRepository.findByEmail(email);
    }

    // OPTIMIZACIÓN: Cache para passwords desencriptados
    @Cacheable(value = "encryptionCache", key = "#encryptedPassword")
    private String getCachedDecryptedPassword(String encryptedPassword) {
        return encryptionService.decrypt(encryptedPassword);
    }

    @Override
    public Optional<String> extractNetflixHomeLink(String email) {
        try {
            logger.info("INICIANDO PROCESO PARA: {}", email);

            // Usar cache para credenciales
            Optional<EmailCredentials> credentialsOpt = getCachedCredentials(email);
            if (credentialsOpt.isEmpty()) {
                logger.warn("Credenciales no encontradas para: {}", email);
                return Optional.empty();
            }

            EmailCredentials credentials = credentialsOpt.get();
            String decryptedPassword = getCachedDecryptedPassword(credentials.getEncryptedPassword());
            logger.info("Credenciales obtenidas desde cache");

            Store store = connectToEmailServer(credentials, decryptedPassword);
            String netflixLink = findNetflixLinkRecent(store);
            store.close();

            logger.info("PROCESO COMPLETADO");
            return Optional.ofNullable(netflixLink);

        } catch (Exception e) {
            logger.error("Error procesando email {}: {}", email, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> extractTemporaryCode(String email) {
        try {
            logger.info("EXTRAYENDO CODIGO TEMPORAL PARA: {}", email);

            Optional<EmailCredentials> credentialsOpt = getCachedCredentials(email);
            if (credentialsOpt.isEmpty()) {
                logger.warn("Credenciales no encontradas para: {}", email);
                return Optional.empty();
            }

            EmailCredentials credentials = credentialsOpt.get();
            String decryptedPassword = getCachedDecryptedPassword(credentials.getEncryptedPassword());

            Store store = connectToEmailServer(credentials, decryptedPassword);
            String temporaryCode = findTemporaryCodeRecent(store);
            store.close();

            logger.info("PROCESO CODIGO COMPLETADO: {}", temporaryCode != null ? "EXITO" : "SIN RESULTADO");
            return Optional.ofNullable(temporaryCode);

        } catch (Exception e) {
            logger.error("Error extrayendo codigo temporal para {}: {}", email, e.getMessage());
            return Optional.empty();
        }
    }

    // OPTIMIZACIÓN: Cache para validaciones de conexión (evita reconectar constantemente)
    @Cacheable(value = "connectionCache", key = "#email")
    @Override
    public boolean validateEmailConnection(String email) {
        try {
            Optional<EmailCredentials> credentialsOpt = getCachedCredentials(email);
            if (credentialsOpt.isEmpty()) {
                return false;
            }

            EmailCredentials credentials = credentialsOpt.get();
            String decryptedPassword = getCachedDecryptedPassword(credentials.getEncryptedPassword());

            Store store = connectToEmailServer(credentials, decryptedPassword);
            boolean isConnected = store.isConnected();
            store.close();

            return isConnected;
        } catch (Exception e) {
            logger.error("Error validando conexion para email {}: {}", email, e.getMessage());
            return false;
        }
    }

    private Store connectToEmailServer(EmailCredentials credentials, String password) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", credentials.getImapHost());
        props.put("mail.imaps.port", credentials.getImapPort().toString());
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.ssl.trust", "*");
        // OPTIMIZACIÓN: Timeouts más cortos para reducir tiempo de espera
        props.put("mail.imaps.connectiontimeout", "5000");
        props.put("mail.imaps.timeout", "8000");
        props.put("mail.imaps.writetimeout", "5000");

        Session session = Session.getInstance(props);
        Store store = session.getStore("imaps");

        logger.debug("Conectando a {} ({})", credentials.getProvider(), credentials.getImapHost());
        store.connect(credentials.getImapHost(), credentials.getEmail(), password);

        return store;
    }

    private String findNetflixLinkRecent(Store store) throws MessagingException, IOException {
        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_ONLY);

        logger.info("Buscando el mensaje MÁS RECIENTE (último que llegó)");

        // CAMBIO: No filtrar por fecha, buscar en TODOS los mensajes para obtener el más reciente
        Message[] allMessages = inbox.getMessages();

        if (allMessages.length == 0) {
            logger.warn("No hay mensajes en la bandeja");
            inbox.close(false);
            return null;
        }

        // El último mensaje del array es el MÁS RECIENTE que llegó
        Message mostRecentMessage = allMessages[allMessages.length - 1];

        logger.info("Analizando ÚNICAMENTE el mensaje más reciente: #{} de {} total",
                allMessages.length, allMessages.length);

        String result = processLastMessageForHome(mostRecentMessage);

        inbox.close(false);
        return result;
    }

    private String processLastMessageForHome(Message msg) throws MessagingException, IOException {
        try {
            String subject = msg.getSubject();
            Date receivedDate = msg.getReceivedDate();
            logger.info("Analizando mensaje MÁS RECIENTE - Asunto: {} - Fecha: {}",
                    subject != null ? subject : "Sin asunto",
                    receivedDate != null ? receivedDate : "Sin fecha");

            String content = extractMessageContent(msg);

            if (content != null && isHomeUpdateContent(content)) {
                logger.info("EMAIL DE HOGAR DETECTADO en mensaje más reciente");
                String link = findNetflixUrlInContent(content);

                if (link != null) {
                    logger.info("Enlace encontrado en mensaje más reciente: {}",
                            link.substring(0, Math.min(60, link.length())) + "...");

                    if (validateNetflixHomeLinkOptimized(link)) {
                        logger.info("ENLACE VALIDADO en mensaje más reciente");
                        return link;
                    } else {
                        logger.warn("Enlace del mensaje más reciente no es válido: sin botón 'Confirmar actualización'");
                    }
                }
            } else {
                logger.info("El mensaje más reciente NO es de actualización de hogar");
            }
        } catch (Exception e) {
            logger.warn("Error procesando mensaje más reciente: {}", e.getMessage());
        }

        logger.warn("No se encontró enlace de hogar válido en el mensaje más reciente");
        return null;
    }

    private String findTemporaryCodeRecent(Store store) throws MessagingException, IOException {
        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_ONLY);

        logger.info("Buscando el mensaje MÁS RECIENTE para código temporal");

        // Obtener TODOS los mensajes para encontrar el más reciente
        Message[] allMessages = inbox.getMessages();

        if (allMessages.length == 0) {
            logger.warn("No hay mensajes en la bandeja");
            inbox.close(false);
            return null;
        }

        // El último mensaje es el más reciente
        Message mostRecentMessage = allMessages[allMessages.length - 1];

        logger.info("Analizando ÚNICAMENTE el mensaje más reciente para código: #{} de {} total",
                allMessages.length, allMessages.length);

        String result = processLastMessageForCode(mostRecentMessage);

        inbox.close(false);
        return result;
    }

    private String processLastMessageForCode(Message msg) throws MessagingException, IOException {
        try {
            String subject = msg.getSubject();
            Date receivedDate = msg.getReceivedDate();
            logger.info("Analizando mensaje MÁS RECIENTE para código - Asunto: {} - Fecha: {}",
                    subject != null ? subject : "Sin asunto",
                    receivedDate != null ? receivedDate : "Sin fecha");

            String content = extractMessageContent(msg);

            if (content != null && isTemporaryCodeContent(content)) {
                logger.info("EMAIL DE CODIGO TEMPORAL DETECTADO en mensaje más reciente");
                String codeUrl = findTemporaryCodeUrl(content);
                if (codeUrl != null) {
                    return extractCodeFromUrl(codeUrl);
                }
            } else {
                logger.info("El mensaje más reciente NO es de código temporal");
            }
        } catch (Exception e) {
            logger.warn("Error procesando mensaje más reciente para código: {}", e.getMessage());
        }

        logger.warn("No se encontró código temporal en el mensaje más reciente");
        return null;
    }

    private String extractMessageContent(Message message) throws MessagingException, IOException {
        StringBuilder content = new StringBuilder();

        if (message.isMimeType("text/html")) {
            content.append((String) message.getContent());
        } else if (message.isMimeType("text/plain")) {
            content.append((String) message.getContent());
        } else if (message.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) message.getContent();
            extractFromMultipart(multipart, content);
        }

        return content.toString();
    }

    private void extractFromMultipart(Multipart multipart, StringBuilder content) throws MessagingException, IOException {
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart bodyPart = multipart.getBodyPart(i);

            if (bodyPart.isMimeType("text/html")) {
                content.append(bodyPart.getContent().toString());
                break;
            } else if (bodyPart.isMimeType("text/plain") && content.length() == 0) {
                content.append(bodyPart.getContent().toString());
            } else if (bodyPart.isMimeType("multipart/*")) {
                extractFromMultipart((Multipart) bodyPart.getContent(), content);
            }
        }
    }

    private String findNetflixUrlInContent(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }

        // OPTIMIZACIÓN: Patrones más específicos basados en tu captura de pantalla
        Pattern specificPattern = Pattern.compile(
                "https?://(?:www\\.)?netflix\\.com/[^\\s\"'<>)]*(?:household|manage|actualizar|update|hogar|verify|confirm)[^\\s\"'<>)]*",
                Pattern.CASE_INSENSITIVE
        );

        Matcher specificMatcher = specificPattern.matcher(content);
        while (specificMatcher.find()) {
            String url = specificMatcher.group();
            if (isRelevantNetflixUrl(url)) {
                logger.info("URL específica de hogar encontrada");
                return url;
            }
        }

        // Patrón de respaldo para href
        Pattern hrefPattern = Pattern.compile(
                "href=[\"'](https?://[^\"']*netflix\\.com[^\"']*(?:household|hogar|update|actualizar|manage)[^\"']*)[\"']",
                Pattern.CASE_INSENSITIVE
        );

        Matcher hrefMatcher = hrefPattern.matcher(content);
        if (hrefMatcher.find()) {
            String url = hrefMatcher.group(1);
            if (isRelevantNetflixUrl(url)) {
                logger.info("URL de hogar en href encontrada");
                return url;
            }
        }

        logger.debug("No se encontraron URLs específicas de actualizar hogar");
        return null;
    }

    // OPTIMIZACIÓN: Detección más específica basada en tu captura
    private boolean isHomeUpdateContent(String content) {
        String lowerContent = content.toLowerCase();

        // Palabras clave específicas de la página que mostraste
        String[] specificKeywords = {
                "completa la actualización del hogar",
                "confirma que quieres configurar tu hogar",
                "actualización podría limitar",
                "dispositivos que no sean parte del hogar"
        };

        // Si encuentra cualquier frase específica, es definitivamente un email de hogar
        for (String specific : specificKeywords) {
            if (lowerContent.contains(specific)) {
                logger.info("Email de hogar confirmado por frase específica: {}", specific);
                return true;
            }
        }

        // Fallback a palabras generales
        String[] requiredKeywords = {"hogar", "actualizar", "household", "solicitud", "dispositivos"};
        int keywordCount = 0;
        for (String keyword : requiredKeywords) {
            if (lowerContent.contains(keyword)) {
                keywordCount++;
            }
        }

        boolean isRelevant = keywordCount >= 3; // Aumentado de 2 a 3 para mayor precisión
        logger.debug("Contenido de hogar por palabras generales: {} ({}/{})",
                isRelevant, keywordCount, requiredKeywords.length);

        return isRelevant;
    }

    private boolean isRelevantNetflixUrl(String url) {
        String lowerUrl = url.toLowerCase();
        return lowerUrl.contains("household") ||
                lowerUrl.contains("manage") ||
                lowerUrl.contains("update") ||
                lowerUrl.contains("hogar") ||
                lowerUrl.contains("actualizar") ||
                lowerUrl.contains("verify") ||
                lowerUrl.contains("confirm");
    }

    private boolean isTemporaryCodeContent(String content) {
        String lowerContent = content.toLowerCase();
        String[] codeKeywords = {"codigo", "temporal", "acceso", "obtener", "dispositivo", "ver netflix"};

        int keywordCount = 0;
        for (String keyword : codeKeywords) {
            if (lowerContent.contains(keyword)) {
                keywordCount++;
            }
        }

        return keywordCount >= 2;
    }

    private String findTemporaryCodeUrl(String content) {
        Pattern codeButtonPattern = Pattern.compile(
                "href=[\"'](https?://[^\"']*netflix\\.com[^\"']*)[\"'][^>]*>\\s*(?:Obtener\\s+codigo|Obtener|Get\\s+code)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher codeButtonMatcher = codeButtonPattern.matcher(content);
        if (codeButtonMatcher.find()) {
            return codeButtonMatcher.group(1);
        }

        Pattern codePattern = Pattern.compile(
                "href=[\"'](https?://[^\"']*netflix\\.com[^\"']*(?:code|codigo|temporal|access)[^\"']*)[\"']",
                Pattern.CASE_INSENSITIVE
        );

        Matcher codeMatcher = codePattern.matcher(content);
        if (codeMatcher.find()) {
            return codeMatcher.group(1);
        }

        return null;
    }

    private String extractCodeFromUrl(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return extractFourDigitCode(response.body());
            }
        } catch (Exception e) {
            logger.error("Error obteniendo codigo: {}", e.getMessage());
        }
        return null;
    }

    private String extractFourDigitCode(String pageContent) {
        // Patrón específico: buscar después del texto completo
        Pattern specificPattern = Pattern.compile(
                "ingresa\\s+este\\s+c[óo]digo\\s+en\\s+el\\s+dispositivo\\s+solicitante\\s+para\\s+obtener\\s+acceso\\s+temporal[^\\d]*(\\d)\\s+(\\d)\\s+(\\d)\\s+(\\d)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher specificMatcher = specificPattern.matcher(pageContent);
        if (specificMatcher.find()) {
            return specificMatcher.group(1) + specificMatcher.group(2) +
                    specificMatcher.group(3) + specificMatcher.group(4);
        }

        // Fallback más corto
        Pattern fallbackPattern = Pattern.compile(
                "(?:ingresa\\s+este\\s+c[óo]digo|acceso\\s+temporal)[^\\d]*(\\d)\\s+(\\d)\\s+(\\d)\\s+(\\d)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher fallbackMatcher = fallbackPattern.matcher(pageContent);
        if (fallbackMatcher.find()) {
            return fallbackMatcher.group(1) + fallbackMatcher.group(2) +
                    fallbackMatcher.group(3) + fallbackMatcher.group(4);
        }

        return null;
    }

    // OPTIMIZACIÓN: Validación más eficiente con timeout corto
    private boolean validateNetflixHomeLinkOptimized(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8)) // Timeout reducido
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // OPTIMIZACIÓN: Usar búsqueda de texto simple antes de parsear HTML
                String bodyLower = response.body().toLowerCase();

                if (bodyLower.contains("confirmar actualización") ||
                        bodyLower.contains("confirm update") ||
                        bodyLower.contains("completa la actualización del hogar")) {
                    logger.debug("Validación rápida exitosa por contenido de texto");
                    return true;
                }

                // Solo parsear HTML si la búsqueda rápida no funciona
                Document doc = Jsoup.parse(response.body());
                Elements buttons = doc.select("button, a, input[type=submit]");

                for (Element btn : buttons) {
                    String text = btn.text().trim().toLowerCase();
                    if (text.contains("confirmar actualización") ||
                            text.contains("confirmar") && text.contains("hogar")) {
                        logger.debug("Botón de confirmación detectado en HTML");
                        return true;
                    }
                }
            } else {
                logger.warn("HTTP {}: Enlace posiblemente expirado", response.statusCode());
            }
        } catch (Exception e) {
            logger.error("Error validando enlace Netflix: {}", e.getMessage());
        }
        return false;
    }
}