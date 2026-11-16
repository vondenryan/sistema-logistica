package logistics.shared;

import java.util.UUID;

public interface SimulatedPaymentPort {
  void ensureApproved(UUID orderId);
}
