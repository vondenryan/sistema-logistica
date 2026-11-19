package logistics.billing.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import logistics.shared.SimulatedPaymentPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SimulatedPaymentAdapter implements SimulatedPaymentPort {
  private final JdbcTemplate db;

  public SimulatedPaymentAdapter(JdbcTemplate db) {
    this.db = db;
  }

  public void ensureApproved(UUID orderId) {
    db.update(
        "INSERT INTO billing.simulated_payments(order_id,status,created_at) VALUES"
            + " (?,'APPROVED',?)",
        orderId,
        Timestamp.from(Instant.now()));
  }
}
