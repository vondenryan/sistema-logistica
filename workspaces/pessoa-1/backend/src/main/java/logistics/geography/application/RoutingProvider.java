package logistics.geography.application;

import contracts.v1.AddressSnapshot;
import java.math.BigDecimal;

public interface RoutingProvider {
  BigDecimal roadDistanceKm(AddressSnapshot from, AddressSnapshot to);

  String source();
}
