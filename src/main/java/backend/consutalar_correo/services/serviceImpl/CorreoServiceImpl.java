package backend.consutalar_correo.services.serviceImpl;

import backend.consutalar_correo.config.ImapProperties;
import backend.consutalar_correo.services.CorreoService;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import org.springframework.stereotype.Service;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CorreoServiceImpl implements CorreoService {

    private final ImapProperties props;

    // Enlaces de Netflix (puedes ajustar si ves otros patrones reales)
    private static final Pattern NETFLIX_LINK =
            Pattern.compile("(https?://(?:www\\.)?netflix\\.com[\\w\\-./?=&%]+)", Pattern.CASE_INSENSITIVE);

    public CorreoServiceImpl(ImapProperties props) {
        this.props = props;
    }

    @Override
    public String findNetflixUpdateHomeLinkFor(String correoBuscado) {
        try {
            Store store = connect();
            Folder inbox = store.getFolder(props.getFolder());
            inbox.open(Folder.READ_ONLY);

            int total = inbox.getMessageCount();
            if (total == 0) {
                close(inbox, store);
                return null;
            }

            int from = Math.max(1, total - props.getLastMessagesToScan() + 1);
            Message[] msgs = inbox.getMessages(from, total);

            // Recorremos del más reciente al más antiguo
            for (int i = msgs.length - 1; i >= 0; i--) {
                Message m = msgs[i];

                // 1) Filtrar por asunto o remitente
                String subject = safeLower(m.getSubject());
                String fromAddr = extractFrom(m);

                boolean pareceNetflix = subject.contains("netflix")
                        || fromAddr.contains("netflix");

                if (!pareceNetflix) continue;

                // 2) Ver si el correo está “relacionado” al correoBuscado:
                //    a) aparece en destinatarios
                //    b) aparece en el cuerpo (por reenvío)
                if (!isRelatedToRecipient(m, correoBuscado) && !bodyContainsRecipient(m, correoBuscado)) {
                    continue;
                }

                // 3) Extraer cuerpo y buscar el enlace
                String body = extractBodyText(m);
                if (body == null || body.isBlank()) continue;

                Matcher matcher = NETFLIX_LINK.matcher(body);
                while (matcher.find()) {
                    String link = matcher.group(1);
                    // Opcional: filtrar por rutas típicas de “hogar”
                    if (looksLikeUpdateHome(link)) {
                        close(inbox, store);
                        return link;
                    }
                }
            }

            close(inbox, store);
            return null;

        } catch (Exception e) {
            // Loguea el error en producción
            return null;
        }
    }

    private Store connect() throws Exception {
        Properties p = new Properties();
        p.put("mail.store.protocol", "imaps");
        p.put("mail.imaps.host", props.getHost());
        p.put("mail.imaps.port", String.valueOf(props.getPort()));
        p.put("mail.imaps.ssl.enable", String.valueOf(props.isSsl()));

        Session session = Session.getInstance(p);
        Store store = session.getStore("imaps");
        store.connect(props.getHost(), props.getUsername(), props.getPassword());
        return store;
    }

    private void close(Folder inbox, Store store) {
        try { if (inbox != null && inbox.isOpen()) inbox.close(false); } catch (Exception ignored) {}
        try { if (store != null && store.isConnected()) store.close(); } catch (Exception ignored) {}
    }

    private String safeLower(String s) { return s == null ? "" : s.toLowerCase(); }

    private String extractFrom(Message m) {
        try {
            Address[] from = m.getFrom();
            if (from != null && from.length > 0) {
                return safeLower(((InternetAddress) from[0]).getAddress());
            }
        } catch (Exception ignored) {}
        return "";
    }

    private boolean isRelatedToRecipient(Message m, String correoBuscado) {
        try {
            Address[] to = m.getRecipients(Message.RecipientType.TO);
            Address[] cc = m.getRecipients(Message.RecipientType.CC);
            Address[] bcc = m.getRecipients(Message.RecipientType.BCC);

            if (containsAddress(to, correoBuscado)) return true;
            if (containsAddress(cc, correoBuscado)) return true;
            if (containsAddress(bcc, correoBuscado)) return true;
        } catch (Exception ignored) {}
        return false;
    }

    private boolean containsAddress(Address[] addrs, String correo) {
        if (addrs == null) return false;
        String correoLower = correo.toLowerCase();
        for (Address a : addrs) {
            if (a instanceof InternetAddress ia) {
                String addr = safeLower(ia.getAddress());
                if (addr.equals(correoLower)) return true;
            }
        }
        return false;
    }

    private boolean bodyContainsRecipient(Message m, String correoBuscado) {
        try {
            String body = extractBodyText(m);
            return body != null && body.toLowerCase().contains(correoBuscado.toLowerCase());
        } catch (Exception ignored) {}
        return false;
    }

    private String extractBodyText(Message m) throws Exception {
        Object content = m.getContent();
        if (content instanceof String s) {
            return s;
        } else if (content instanceof Multipart mp) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart part = mp.getBodyPart(i);
                if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                    continue;
                }
                if (part.isMimeType("text/plain") || part.isMimeType("text/html")) {
                    sb.append(part.getContent().toString()).append("\n");
                } else if (part.getContent() instanceof Multipart nested) {
                    // partes anidadas (HTML con imágenes, etc.)
                    for (int j = 0; j < nested.getCount(); j++) {
                        BodyPart np = nested.getBodyPart(j);
                        if (np.isMimeType("text/plain") || np.isMimeType("text/html")) {
                            sb.append(np.getContent().toString()).append("\n");
                        }
                    }
                }
            }
            return sb.toString();
        }
        return null;
    }

    private boolean looksLikeUpdateHome(String link) {
        // Heurística simple: enlaces de acciones/confirmaciones de Netflix suelen incluir rutas o params únicos
        // Puedes ajustar esta lista según los correos reales que recibas
        String l = link.toLowerCase();
        if (!l.contains("netflix.com")) return false;

        return l.contains("update") || l.contains("home")
                || l.contains("verify") || l.contains("confirm")
                || l.contains("set") || l.contains("manage");
    }
}
