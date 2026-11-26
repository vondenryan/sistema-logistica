package logistics.notifications.application;

public interface MailSenderPort {
  void send(String to, String subject, String body);
}
