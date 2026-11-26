package logistics.notifications.infrastructure;

import logistics.notifications.application.NotificationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(
    name = "notifications.outbox.scheduler-enabled",
    havingValue = "true",
    matchIfMissing = false)
public class OutboxScheduler {
  private final NotificationService notificationService;

  public OutboxScheduler(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @Scheduled(fixedDelayString = "${notifications.outbox.poll-interval-ms:5000}")
  public void poll() {
    notificationService.processPending(20);
  }
}
