package logistics.deliveries.application;

import contracts.v1.DeliveryStatus;
import java.time.Instant;

public record TrackingMilestone(DeliveryStatus status, Instant at) {}
