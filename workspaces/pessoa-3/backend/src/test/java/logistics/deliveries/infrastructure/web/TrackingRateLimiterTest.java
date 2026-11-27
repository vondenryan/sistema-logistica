package logistics.deliveries.infrastructure.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TrackingRateLimiterTest {
  private int clients(TrackingRateLimiter limiter) {
    return ((Map<?, ?>) ReflectionTestUtils.getField(limiter, "requestsByClient")).size();
  }

  @Test
  void fullMapRejectsNewClientsWithoutResettingActiveLimits() {
    var limiter = new TrackingRateLimiter(1, 60, 2, Clock.systemUTC());
    assertTrue(limiter.allow("A"));
    assertTrue(limiter.allow("B"));
    for (int i = 0; i < 100; i++) assertFalse(limiter.allow("new" + i));
    assertEquals(2, clients(limiter));
    assertFalse(limiter.allow("A"));
  }

  @Test
  void requestsReclaimAllExpiredClientsIncludingThoseThatNeverReturn() {
    var clock = mock(Clock.class);
    var now = Instant.parse("2026-11-24T12:00:00Z");
    when(clock.instant()).thenReturn(now);
    var limiter = new TrackingRateLimiter(1, 60, 2, clock);
    limiter.allow("A");
    limiter.allow("B");
    when(clock.instant()).thenReturn(now.plusSeconds(60));
    assertTrue(limiter.allow("C"));
    assertEquals(1, clients(limiter));
    assertTrue(limiter.allow("A"));
  }

  @Test
  void slidingWindowExpiresOldRequestsWithoutDroppingRecentOnes() {
    var clock = mock(Clock.class);
    var now = Instant.parse("2026-11-24T12:00:00Z");
    when(clock.instant()).thenReturn(now);
    var limiter = new TrackingRateLimiter(2, 60, 2, clock);
    assertTrue(limiter.allow("A"));
    when(clock.instant()).thenReturn(now.plusSeconds(30));
    assertTrue(limiter.allow("A"));
    assertFalse(limiter.allow("A"));
    when(clock.instant()).thenReturn(now.plusSeconds(60));
    assertTrue(limiter.allow("A"));
    assertFalse(limiter.allow("A"));
  }

  @Test
  void concurrentClientsCannotExceedMemoryCap() throws Exception {
    var limiter = new TrackingRateLimiter(1, 60, 8, Clock.systemUTC());
    try (var executor = Executors.newFixedThreadPool(4)) {
      var tasks =
          IntStream.range(0, 64)
              .mapToObj(i -> (Callable<Boolean>) () -> limiter.allow("ip" + i))
              .toList();
      int accepted = 0;
      for (var result : executor.invokeAll(tasks)) if (result.get()) accepted++;
      assertEquals(8, accepted);
      assertEquals(8, clients(limiter));
    }
  }
}
