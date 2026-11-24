package logistics.deliveries.infrastructure.web;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TrackingRateLimiter {
  private final int maxRequests;
  private final long windowSeconds;
  private final int maxClients;
  private final Clock clock;
  private final Map<String, Deque<Instant>> requestsByClient = new HashMap<>();

  @Autowired
  public TrackingRateLimiter(
      @Value("${tracking.rate-limit.max-requests:30}") int maxRequests,
      @Value("${tracking.rate-limit.window-seconds:60}") long windowSeconds,
      @Value("${tracking.rate-limit.max-clients:10000}") int maxClients) {
    this(maxRequests, windowSeconds, maxClients, Clock.systemUTC());
  }

  public TrackingRateLimiter(int maxRequests, long windowSeconds, Clock clock) {
    this(maxRequests, windowSeconds, 10000, clock);
  }

  TrackingRateLimiter(int maxRequests, long windowSeconds, int maxClients, Clock clock) {
    this.maxRequests = Math.max(1, maxRequests);
    this.windowSeconds = Math.max(1L, windowSeconds);
    this.maxClients = Math.max(1, maxClients);
    this.clock = clock;
  }

  public synchronized boolean allow(String clientKey) {
    String key = (clientKey == null || clientKey.isBlank()) ? "unknown" : clientKey;
    Instant now = clock.instant();
    Instant cutoff = now.minusSeconds(windowSeconds);

    // Reclaim inactive clients as requests arrive, including those never seen again.
    requestsByClient.values().removeIf(timestamps -> !timestamps.peekLast().isAfter(cutoff));
    Deque<Instant> timestamps = requestsByClient.get(key);
    if (timestamps == null) {
      // Do not evict active clients: that would reset their rate-limit windows.
      if (requestsByClient.size() >= maxClients) return false;
      timestamps = new ArrayDeque<>();
      requestsByClient.put(key, timestamps);
    }
    while (!timestamps.isEmpty() && !timestamps.peekFirst().isAfter(cutoff)) {
      timestamps.pollFirst();
    }
    if (timestamps.size() >= maxRequests) return false;
    timestamps.addLast(now);
    return true;
  }

  public long windowSeconds() {
    return windowSeconds;
  }

  public synchronized void reset() {
    requestsByClient.clear();
  }
}
