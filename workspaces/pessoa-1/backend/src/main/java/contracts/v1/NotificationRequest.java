package contracts.v1;

import java.math.*;
import java.time.*;
import java.util.*;

public record NotificationRequest(
    UUID eventId, NotificationEventType eventType, UUID orderId, String to, Instant occurredAt) {}
