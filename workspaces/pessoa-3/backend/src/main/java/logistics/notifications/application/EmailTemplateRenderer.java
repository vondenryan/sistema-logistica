package logistics.notifications.application;

import contracts.v1.NotificationEventType;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class EmailTemplateRenderer {

  public record RenderedEmail(String subject, String body) {}

  public RenderedEmail render(
      NotificationEventType eventType, UUID orderId, Instant occurredAt) {
    return switch (eventType) {
      case ORDER_CONFIRMED ->
          new RenderedEmail(
              "Pedido confirmado",
              "Seu pedido foi confirmado e está sendo preparado para envio.\n"
                  + "Atualização registrada em: "
                  + occurredAt);
      case DELIVERY_IN_TRANSIT ->
          new RenderedEmail(
              "Entrega em trânsito",
              "Sua entrega saiu para transporte e está em trânsito.\n"
                  + "Atualização registrada em: "
                  + occurredAt);
      case DELIVERY_DELIVERED ->
          new RenderedEmail(
              "Entrega concluída",
              "Sua entrega foi concluída com sucesso.\n"
                  + "Atualização registrada em: "
                  + occurredAt);
      case DELIVERY_FAILED_ATTEMPT ->
          new RenderedEmail(
              "Tentativa de entrega sem sucesso",
              "Houve uma tentativa de entrega sem sucesso. Uma nova tentativa será programada.\n"
                  + "Atualização registrada em: "
                  + occurredAt);
    };
  }
}
