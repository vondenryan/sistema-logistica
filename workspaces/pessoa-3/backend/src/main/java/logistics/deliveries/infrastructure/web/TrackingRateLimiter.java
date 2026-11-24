package logistics.deliveries.infrastructure.web;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TrackingRateLimiter {
  private final int maxRequests;
  private final long windowSeconds;
  private final Clock clock;
  private final Map<String, Deque<Instant>> requestsByClient = new ConcurrentHashMap<>();

  @Autowired
  public TrackingRateLimiter(
      @Value("${tracking.rate-limit.max-requests:30}") int maxRequests,
      @Value("${tracking.rate-limit.window-seconds:60}") long windowSeconds) {
    this(maxRequests, windowSeconds, Clock.systemUTC());
  }

  public TrackingRateLimiter(int maxRequests, long windowSeconds, Clock clock) {
    this.maxRequests = Math.max(1, maxRequests);
    this.windowSeconds = Math.max(1L, windowSeconds);
    this.clock = clock;
  }

  public boolean allow(String clientKey) {
    String key = (clientKey == null || clientKey.isBlank()) ? "unknown" : clientKey;
    Instant now = clock.instant();
    Instant cutoff = now.minusSeconds(windowSeconds);

    Deque<Instant> timestamps =
        requestsByClient.computeIfAbsent(key, ignored -> new ArrayDeque<>());

    synchronized (timestamps) {
      while (!timestamps.isEmpty() && !timestamps.peekFirst().isAfter(cutoff)) {
        timestamps.pollFirst();
      }
      if (timestamps.size() >= maxRequests) {
        return false;
      }
      timestamps.addLast(now);
      return true;
    }
  }

  public long windowSeconds() {
    return windowSeconds;
  }

  public void reset() {
    requestsByClient.clear();
  }
}
