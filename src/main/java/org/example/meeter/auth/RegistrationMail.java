package org.example.meeter.auth;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class RegistrationMail {
    private final JavaMailSenderImpl sender = new JavaMailSenderImpl();
    private final String appUrl;

    RegistrationMail(@Value("${GMAIL_SMTP_USER:}") String address,
                     @Value("${GMAIL_APP_PASSWORD:}") String password,
                     @Value("${MEETER_PUBLIC_URL:}") String appUrl) {
        this.appUrl = appUrl.strip().replaceAll("/+$", "");
        sender.setHost("smtp.gmail.com");
        sender.setPort(587);
        sender.setUsername(address.strip());
        sender.setPassword(password.replace(" ", ""));
        var properties = sender.getJavaMailProperties();
        properties.setProperty("mail.smtp.auth", "true");
        properties.setProperty("mail.smtp.starttls.required", "true");
        properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        properties.setProperty("mail.smtp.connectiontimeout", "5000");
        properties.setProperty("mail.smtp.timeout", "5000");
        properties.setProperty("mail.smtp.writetimeout", "5000");
    }

    public void send(String email, String token) {
        if (sender.getUsername().isBlank() || sender.getPassword().isBlank() || !validAppUrl())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Wysyłka wiadomości potwierdzających jest obecnie niedostępna.");
        var message = new SimpleMailMessage();
        message.setFrom(sender.getUsername());
        message.setTo(email);
        message.setSubject("Potwierdź adres e-mail w Meeterze");
        message.setText("Aby zakończyć rejestrację w Meeterze, otwórz link:\n\n"
            + appUrl + "/#potwierdz-email=" + token
            + "\n\nLink działa do momentu wygaśnięcia zaproszenia. Jeśli nie rejestrowano konta, zignoruj tę wiadomość.");
        try {
            sender.send(message);
        } catch (MailException failure) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Nie udało się wysłać wiadomości potwierdzającej. Spróbuj ponownie.");
        }
    }

    private boolean validAppUrl() {
        try {
            var uri = URI.create(appUrl);
            return uri.getHost() != null && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                && ("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme())
                    && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()))));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
