package logistics.deliveries.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import logistics.deliveries.application.PublicTrackingView;
import logistics.deliveries.application.TrackingService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tracking")
public class TrackingController {
  private final TrackingService trackingService;
  private final TrackingRateLimiter rateLimiter;

  private final ClientIpResolver clientIps;

  public TrackingController(
      TrackingService trackingService,
      TrackingRateLimiter rateLimiter,
      ClientIpResolver clientIps) {
    this.trackingService = trackingService;
    this.rateLimiter = rateLimiter;
    this.clientIps = clientIps;
  }

  @GetMapping("/{code}")
  public ResponseEntity<?> track(@PathVariable("code") String code, HttpServletRequest request) {
    String clientIp = clientIps.resolve(request);
    if (!rateLimiter.allow(clientIp)) {
      Object attr = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR);
      String traceId = attr != null ? attr.toString() : null;
      ProblemDetail problem =
          Errors.createProblem("RATE_LIMITED", "Too many tracking requests", traceId);
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
          .header(HttpHeaders.RETRY_AFTER, String.valueOf(rateLimiter.windowSeconds()))
          .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE)
          .body(problem);
    }

    PublicTrackingView view = trackingService.trackByCode(code);
    return ResponseEntity.ok(view);
  }
}
