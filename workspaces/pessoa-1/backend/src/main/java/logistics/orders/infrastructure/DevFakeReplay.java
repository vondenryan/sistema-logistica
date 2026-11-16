package logistics.orders.infrastructure;

import logistics.orders.application.OrderService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Replays durable confirmations into process-local dev fakes after every restart. */
@Component
@Profile("dev")
public class DevFakeReplay {
  private final JdbcTemplate database;
  private final OrderService orders;

  public DevFakeReplay(JdbcTemplate database, OrderService orders) {
    this.database = database;
    this.orders = orders;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void replay() {
    database.update(
        "UPDATE orders.orders SET delivery_done=false, notification_done=false WHERE"
            + " status='CONFIRMED'");
    orders.retry();
  }
}
