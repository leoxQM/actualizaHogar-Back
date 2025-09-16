package backend.consutalar_correo.services.serviceImpl;

import backend.consutalar_correo.entities.EmailCredentials;
import backend.consutalar_correo.repositories.EmailCredentialsRepository;
import backend.consutalar_correo.services.EmailProcessorService;
import backend.consutalar_correo.services.EncryptionService;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Override
    public Optional<String> extractNetflixHomeLink(String email) {
        try {
            logger.info("=== INICIANDO PROCESO PARA: {} ===", email);

            Optional<EmailCredentials> credentialsOpt = emailCredentialsRepository.findByEmail(email);
            if (credentialsOpt.isEmpty()) {
                logger.warn("Credenciales no encontradas para: {}", email);
                return Optional.empty();
            }

            EmailCredentials credentials = credentialsOpt.get();
            String decryptedPassword = encryptionService.decrypt(credentials.getEncryptedPassword());
            logger.info("Credenciales obtenidas y descifradas");

            Store store = connectToEmailServer(credentials, decryptedPassword);

            String netflixLink = findNetflixLinkRecent(store);

            store.close();
            logger.info("=== PROCESO COMPLETADO ===");

            return Optional.ofNullable(netflixLink);

        } catch (Exception e) {
            logger.error("Error procesando email {}: {}", email, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> extractTemporaryCode(String email) {
        try {
            logger.info("=== EXTRAYENDO CODIGO TEMPORAL PARA: {} ===", email);

            Optional<EmailCredentials> credentialsOpt = emailCredentialsRepository.findByEmail(email);
            if (credentialsOpt.isEmpty()) {
                logger.warn("Credenciales no encontradas para: {}", email);
                return Optional.empty();
            }

            EmailCredentials credentials = credentialsOpt.get();
            String decryptedPassword = encryptionService.decrypt(credentials.getEncryptedPassword());
            logger.info("Credenciales obtenidas para codigo temporal");

            Store store = connectToEmailServer(credentials, decryptedPassword);

            String temporaryCode = findTemporaryCodeRecent(store);

            store.close();
            logger.info("=== PROCESO CODIGO COMPLETADO: {} ===", temporaryCode != null ? "EXITO" : "SIN RESULTADO");

            return Optional.ofNullable(temporaryCode);

        } catch (Exception e) {
            logger.error("Error extrayendo codigo temporal para {}: {}", email, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean validateEmailConnection(String email) {
        try {
            Optional<EmailCredentials> credentialsOpt = emailCredentialsRepository.findByEmail(email);
            if (credentialsOpt.isEmpty()) {
                return false;
            }

            EmailCredentials credentials = credentialsOpt.get();
            String decryptedPassword = encryptionService.decrypt(credentials.getEncryptedPassword());

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
        props.put("mail.imaps.connectiontimeout", "8000");
        props.put("mail.imaps.timeout", "10000");
        props.put("mail.imaps.writetimeout", "8000");

        Session session = Session.getInstance(props);
        Store store = session.getStore("imaps");

        logger.info("Conectando a {} ({})...", credentials.getProvider(), credentials.getImapHost());

        store.connect(credentials.getImapHost(), credentials.getEmail(), password);

        return store;
    }

    private String findNetflixLinkRecent(Store store) throws MessagingException, IOException {
        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_ONLY);

        logger.info("Buscando emails recientes (últimos 2 días)...");

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -2);
        Date twoDaysAgo = cal.getTime();

        SearchTerm recentTerm = new ReceivedDateTerm(ComparisonTerm.GE, twoDaysAgo);
        Message[] messages = inbox.search(recentTerm);

        logger.info("Encontrados {} mensajes en los últimos 2 días", messages.length);

        if (messages.length == 0) {
            logger.warn("No se encontraron mensajes recientes");
            inbox.close(false);
            return null;
        }

        // Tomar solo el último mensaje
        Message lastMessage = messages[messages.length - 1];
        String result = processLastMessageForHome(lastMessage);

        inbox.close(false);
        return result;
    }

    private String processLastMessageForHome(Message msg) throws MessagingException, IOException {
        try {
            String subject = msg.getSubject();
            logger.info("Analizando ÚLTIMO mensaje: {}", subject != null ? subject : "Sin asunto");

            String content = extractMessageContent(msg);

            if (content != null && isHomeUpdateContent(content)) {
                logger.info("EMAIL DE HOGAR DETECTADO");
                String link = findNetflixUrlInContent(content);

                if (link != null) {
                    logger.info("Enlace encontrado: {}", link);
                    if (validateNetflixHomeLink(link)) {
                        logger.info("ENLACE VALIDADO: {}", link);
                        return link;
                    } else {
                        logger.warn("El enlace no es válido: no contiene botón 'Confirmar actualización'");
                        return null;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error procesando último mensaje: {}", e.getMessage());
        }

        logger.warn("No se encontró enlace de hogar válido en el último correo");
        return null;
    }

    private String findTemporaryCodeRecent(Store store) throws MessagingException, IOException {
        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_ONLY);

        logger.info("Buscando emails de codigo temporal (ultimos 2 dias)...");

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -2);
        Date twoDaysAgo = cal.getTime();

        SearchTerm recentTerm = new ReceivedDateTerm(ComparisonTerm.GE, twoDaysAgo);
        Message[] messages = inbox.search(recentTerm);

        logger.info("Encontrados {} mensajes para codigo temporal", messages.length);

        String result = processMessagesForCode(messages);
        inbox.close(false);
        return result;
    }

    private String processMessagesForHome(Message[] messages) throws MessagingException, IOException {
        if (messages.length == 0) {
            logger.warn("No hay mensajes recientes");
            return null;
        }

        for (int i = messages.length - 1; i >= 0; i--) {
            Message msg = messages[i];

            try {
                String subject = msg.getSubject();
                logger.info("Analizando mensaje: {}", subject != null ? subject : "Sin asunto");

                String content = extractMessageContent(msg);

                if (content != null && isHomeUpdateContent(content)) {
                    logger.info("EMAIL DE HOGAR DETECTADO");
                    String link = findNetflixUrlInContent(content);
                    if (link != null) {
                        logger.info("Enlace encontrado, validando...");
                        if (validateNetflixHomeLink(link)) {
                            logger.info("ENLACE VALIDADO: {}", link);
                            return link;
                        } else {
                            logger.warn("Enlace descartado: no contiene boton Confirmar actualización");
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Error procesando mensaje: {}", e.getMessage());
                continue;
            }
        }

        logger.warn("No se encontro enlace de hogar valido");
        return null;
    }

    private String processMessagesForCode(Message[] messages) throws MessagingException, IOException {
        if (messages.length == 0) {
            logger.warn("No hay mensajes recientes para codigo");
            return null;
        }

        for (int i = messages.length - 1; i >= 0; i--) {
            Message msg = messages[i];

            try {
                String subject = msg.getSubject();
                logger.info("Analizando mensaje para codigo: {}", subject != null ? subject : "Sin asunto");

                String content = extractMessageContent(msg);

                if (content != null && isTemporaryCodeContent(content)) {
                    logger.info("EMAIL DE CODIGO TEMPORAL DETECTADO");
                    String codeUrl = findTemporaryCodeUrl(content);
                    if (codeUrl != null) {
                        return extractCodeFromUrl(codeUrl);
                    }
                }
            } catch (Exception e) {
                logger.warn("Error procesando mensaje para codigo: {}", e.getMessage());
                continue;
            }
        }

        logger.warn("No se encontro codigo temporal valido");
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

        logger.debug("Buscando URLs en contenido de {} caracteres", content.length());

        Pattern directPattern = Pattern.compile(
                "https?://(?:www\\.)?netflix\\.com/[^\\s\"'<>)]*(?:home|household|manage|actualizar|update|hogar|verify|confirm)[^\\s\"'<>)]*",
                Pattern.CASE_INSENSITIVE
        );

        Matcher directMatcher = directPattern.matcher(content);
        while (directMatcher.find()) {
            String url = directMatcher.group();
            if (isRelevantNetflixUrl(url)) {
                logger.info("URL directa de hogar encontrada");
                return url;
            }
        }

        Pattern hrefPattern = Pattern.compile(
                "href=[\"'](https?://[^\"']*netflix\\.com[^\"']*)[\"']",
                Pattern.CASE_INSENSITIVE
        );

        Matcher hrefMatcher = hrefPattern.matcher(content);
        while (hrefMatcher.find()) {
            String url = hrefMatcher.group(1);
            if (isRelevantNetflixUrl(url)) {
                logger.info("URL de hogar en href encontrada");
                return url;
            }
        }

        logger.info("No se encontraron URLs especificas de actualizar hogar");
        return null;
    }

    private boolean isHomeUpdateContent(String content) {
        String lowerContent = content.toLowerCase();

        String[] requiredKeywords = {"hogar", "actualizar", "household", "solicitud", "dispositivos"};

        int keywordCount = 0;
        for (String keyword : requiredKeywords) {
            if (lowerContent.contains(keyword)) {
                keywordCount++;
                logger.debug("Palabra clave encontrada: {}", keyword);
            }
        }

        boolean isRelevant = keywordCount >= 2;
        logger.info("Contenido de hogar: {} (palabras clave: {}/{})",
                isRelevant ? "SI" : "NO", keywordCount, requiredKeywords.length);

        return isRelevant;
    }

    private boolean isRelevantNetflixUrl(String url) {
        String lowerUrl = url.toLowerCase();
        return lowerUrl.contains("home") ||
                lowerUrl.contains("household") ||
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

        boolean isCode = keywordCount >= 2;
        logger.debug("Contenido de codigo: {} (palabras: {}/{})", isCode, keywordCount, codeKeywords.length);
        return isCode;
    }

    private String findTemporaryCodeUrl(String content) {
        Pattern codeButtonPattern = Pattern.compile(
                "href=[\"'](https?://[^\"']*netflix\\.com[^\"']*)[\"'][^>]*>\\s*(?:Obtener\\s+codigo|Obtener|Get\\s+code)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher codeButtonMatcher = codeButtonPattern.matcher(content);
        if (codeButtonMatcher.find()) {
            String url = codeButtonMatcher.group(1);
            logger.info("URL del boton obtener codigo encontrada");
            return url;
        }

        Pattern codePattern = Pattern.compile(
                "href=[\"'](https?://[^\"']*netflix\\.com[^\"']*(?:code|codigo|temporal|access)[^\"']*)[\"']",
                Pattern.CASE_INSENSITIVE
        );

        Matcher codeMatcher = codePattern.matcher(content);
        if (codeMatcher.find()) {
            String url = codeMatcher.group(1);
            logger.info("URL relacionada con codigo encontrada");
            return url;
        }

        logger.warn("No se encontro URL de codigo temporal");
        return null;
    }

    private String extractCodeFromUrl(String url) {
        try {
            logger.info("Obteniendo codigo desde: {}", url);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String code = extractFourDigitCode(response.body());
                if (code != null) {
                    logger.info("CODIGO EXTRAIDO: {}", code);
                    return code;
                } else {
                    logger.warn("No se pudo extraer codigo de la pagina");
                    return null;
                }
            } else {
                logger.error("Error HTTP {}", response.statusCode());
                return null;
            }

        } catch (Exception e) {
            logger.error("Error obteniendo codigo: {}", e.getMessage());
            return null;
        }
    }

    private String extractFourDigitCode(String pageContent) {
        Pattern spacedPattern = Pattern.compile("(\\d)\\s+(\\d)\\s+(\\d)\\s+(\\d)");
        Matcher spacedMatcher = spacedPattern.matcher(pageContent);
        if (spacedMatcher.find()) {
            String code = spacedMatcher.group(1) + spacedMatcher.group(2) +
                    spacedMatcher.group(3) + spacedMatcher.group(4);
            logger.info("Codigo encontrado (espaciado): {}", code);
            return code;
        }

        Pattern contextPattern = Pattern.compile(
                "(?:codigo|code|usa\\s+este)\\s*[:\\-]?\\s*(\\d{4})\\b",
                Pattern.CASE_INSENSITIVE
        );
        Matcher contextMatcher = contextPattern.matcher(pageContent);
        if (contextMatcher.find()) {
            return contextMatcher.group(1);
        }

        Pattern genericPattern = Pattern.compile("\\b(\\d{4})\\b");
        Matcher genericMatcher = genericPattern.matcher(pageContent);

        while (genericMatcher.find()) {
            String code = genericMatcher.group(1);
            if (!code.startsWith("20") && !code.startsWith("19") &&
                    !code.equals("1080") && !code.equals("720")) {
                return code;
            }
        }

        return null;
    }

    // ================= NUEVO MÉTODO =================
    private boolean validateNetflixHomeLink(String url) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Document doc = Jsoup.parse(response.body());
                Elements buttons = doc.select("button, a");

                for (Element btn : buttons) {
                    String text = btn.text().trim().toLowerCase();
                    if (text.contains("confirmar actualización")) {
                        logger.info("Botón 'Confirmar actualización' detectado en el enlace");
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error validando link Netflix: {}", e.getMessage());
        }
        return false;
    }
}
