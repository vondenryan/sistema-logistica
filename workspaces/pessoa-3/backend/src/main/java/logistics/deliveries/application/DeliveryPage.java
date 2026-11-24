package logistics.deliveries.application;

import contracts.v1.DeliverySnapshot;
import java.util.List;

public record DeliveryPage(
    List<DeliverySnapshot> items, int page, int size, long totalItems) {}
