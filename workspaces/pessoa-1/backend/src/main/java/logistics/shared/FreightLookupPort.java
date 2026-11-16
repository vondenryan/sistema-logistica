package logistics.shared;

import java.math.BigDecimal;
import java.util.UUID;

public interface FreightLookupPort {
  BigDecimal latestAmount(UUID orderId);
}
