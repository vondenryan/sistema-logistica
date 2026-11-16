package contracts.v1;

import java.util.*;

public interface OrderSnapshotPort {
  OrderSnapshot getById(UUID orderId);

  OrderSnapshot findByTrackingCode(String trackingCode);
}
