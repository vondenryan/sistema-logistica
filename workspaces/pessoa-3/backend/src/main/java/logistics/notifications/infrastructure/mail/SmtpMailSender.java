package logistics.notifications.infrastructure.mail;

import logistics.notifications.application.MailSenderPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notifications.smtp.enabled", havingValue = "true")
public class SmtpMailSender implements MailSenderPort {
  private final JavaMailSender javaMailSender;
  private final String fromAddress;

  public SmtpMailSender(
      JavaMailSender javaMailSender,
      @Value("${notifications.smtp.from:noreply@logistica-academica.invalid}")
          String fromAddress) {
    this.javaMailSender = javaMailSender;
    this.fromAddress = fromAddress;
  }

  @Override
  public void send(String to, String subject, String body) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(fromAddress);
    message.setTo(to);
    message.setSubject(subject);
    message.setText(body);
    javaMailSender.send(message);
  }
}
