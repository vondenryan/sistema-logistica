package logistics.fakes;

import contracts.v1.AddressSnapshot;
import contracts.v1.ContractException;
import contracts.v1.OrderSnapshot;
import contracts.v1.OrderSnapshotPort;
import contracts.v1.OrderStatus;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class FakeOrderSnapshotPort implements OrderSnapshotPort {
  public static final UUID CANONICAL_ORDER_ID =
      UUID.fromString("44444444-4444-4444-8444-444444444444");
  public static final UUID CANONICAL_STORE_ID =
      UUID.fromString("22222222-2222-4222-8222-222222222222");
  public static final UUID CANONICAL_ORIGIN_ID =
      UUID.fromString("11111111-1111-4111-8111-111111111111");
  public static final String CANONICAL_TRACKING_CODE = "TRK-7M4Q9X2K8P";

  private final Map<UUID, OrderSnapshot> ordersById = new ConcurrentHashMap<>();
  private final Map<String, OrderSnapshot> ordersByTracking = new ConcurrentHashMap<>();

  public FakeOrderSnapshotPort() {
    resetToCanonical();
  }

  public final void resetToCanonical() {
    ordersById.clear();
    ordersByTracking.clear();

    AddressSnapshot origin =
        new AddressSnapshot(
            "Rua Exemplo, 100",
            null,
            "Cidade Fictícia",
            "SC",
            "00000-000",
            "BR",
            new BigDecimal("-26.480000"),
            new BigDecimal("-49.070000"));

    AddressSnapshot destination =
        new AddressSnapshot(
            "Rua Destino, 200",
            null,
            "Cidade Fictícia",
            "SC",
            "00000-000",
            "BR",
            new BigDecimal("-26.490000"),
            new BigDecimal("-49.080000"));

    OrderSnapshot canonicalOrder =
        new OrderSnapshot(
            CANONICAL_ORDER_ID,
            CANONICAL_STORE_ID,
            CANONICAL_ORIGIN_ID,
            OrderStatus.CONFIRMED,
            origin,
            destination,
            "Cliente Fictício",
            "cliente@example.invalid",
            CANONICAL_TRACKING_CODE,
            new BigDecimal("40.000"),
            new BigDecimal("30.00"),
            "BRL");

    register(canonicalOrder);
  }

  public void register(OrderSnapshot order) {
    ordersById.put(order.orderId(), order);
    if (order.trackingCode() != null) {
      ordersByTracking.put(order.trackingCode(), order);
    }
  }

  @Override
  public OrderSnapshot getById(UUID orderId) {
    OrderSnapshot snapshot = ordersById.get(orderId);
    if (snapshot == null) {
      throw new ContractException("NOT_FOUND");
    }
    return snapshot;
  }

  @Override
  public OrderSnapshot findByTrackingCode(String trackingCode) {
    if (trackingCode == null) {
      throw new ContractException("NOT_FOUND");
    }
    OrderSnapshot snapshot = ordersByTracking.get(trackingCode);
    if (snapshot == null) {
      throw new ContractException("NOT_FOUND");
    }
    return snapshot;
  }
}
