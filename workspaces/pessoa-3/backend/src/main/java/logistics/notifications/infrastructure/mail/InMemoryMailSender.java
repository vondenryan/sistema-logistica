package logistics.notifications.infrastructure.mail;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import logistics.notifications.application.MailSenderPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "notifications.smtp.enabled",
    havingValue = "false",
    matchIfMissing = true)
public class InMemoryMailSender implements MailSenderPort {

  public record SentMessage(String to, String subject, String body) {}

  private final List<SentMessage> sentMessages = new CopyOnWriteArrayList<>();
  private final AtomicBoolean alwaysFail = new AtomicBoolean(false);
  private final AtomicInteger failRemaining = new AtomicInteger(0);

  @Override
  public void send(String to, String subject, String body) {
    if (alwaysFail.get() || failRemaining.getAndUpdate(v -> v > 0 ? v - 1 : 0) > 0) {
      throw new IllegalStateException("Simulated SMTP unavailability");
    }
    sentMessages.add(new SentMessage(to, subject, body));
  }

  public List<SentMessage> getSentMessages() {
    return List.copyOf(sentMessages);
  }

  public void setAlwaysFail(boolean fail) {
    alwaysFail.set(fail);
  }

  public void failNext(int count) {
    failRemaining.set(Math.max(0, count));
  }

  public void reset() {
    sentMessages.clear();
    alwaysFail.set(false);
    failRemaining.set(0);
  }
}
