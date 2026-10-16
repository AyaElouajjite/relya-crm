package ma.crm.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Envoi d'e-mails par SMTP (variables MAIL_HOST, MAIL_USERNAME, ...). Sans serveur SMTP configuré,
 * les messages sont écrits dans les logs, ce qui permet de tester les parcours en développement.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String host;
    private final String from;

    public MailService(ObjectProvider<JavaMailSender> mailSender,
                       @Value("${spring.mail.host:}") String host,
                       @Value("${app.mail-from:no-reply@crm.ma}") String from) {
        this.mailSender = mailSender;
        this.host = host;
        this.from = from;
    }

    public record Attachment(String filename, String contentType, byte[] content) {
    }

    public boolean isConfigured() {
        return StringUtils.hasText(host) && mailSender.getIfAvailable() != null;
    }

    public void send(String to, String subject, String body) {
        send(to, subject, body, null);
    }

    /** Asynchrone : un serveur SMTP lent ne bloque pas la requête de l'utilisateur. */
    @Async
    public void send(String to, String subject, String body, Attachment attachment) {
        if (!StringUtils.hasText(to)) {
            return;
        }
        if (!isConfigured()) {
            log.info("[MAIL non envoyé, SMTP non configuré] À : {} | Objet : {}{}\n{}", to, subject,
                    attachment == null ? "" : " | Pièce jointe : " + attachment.filename(), body);
            return;
        }
        try {
            MimeMessage message = mailSender.getObject().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, attachment != null, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            if (attachment != null) {
                helper.addAttachment(attachment.filename(), new ByteArrayResource(attachment.content()),
                        attachment.contentType());
            }
            mailSender.getObject().send(message);
            log.info("E-mail envoyé à {} : {}", to, subject);
        } catch (MessagingException | RuntimeException e) {
            log.error("Échec de l'envoi de l'e-mail à {} : {}", to, e.getMessage());
        }
    }
}
