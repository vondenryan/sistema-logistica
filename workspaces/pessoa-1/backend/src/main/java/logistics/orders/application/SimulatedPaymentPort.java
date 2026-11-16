package logistics.orders.application;

import java.util.UUID;

public interface SimulatedPaymentPort {
  void ensureApproved(UUID orderId);
}
