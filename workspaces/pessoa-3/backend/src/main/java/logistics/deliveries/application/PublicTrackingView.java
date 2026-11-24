package logistics.deliveries.application;

import contracts.v1.DeliveryStatus;
import java.util.List;

public record PublicTrackingView(
    DeliveryStatus status, List<TrackingMilestone> milestones) {}
