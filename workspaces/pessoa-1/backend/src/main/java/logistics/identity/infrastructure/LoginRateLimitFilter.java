package logistics.identity.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.util.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(1)
class LoginRateLimitFilter extends OncePerRequestFilter {
  private record Window(long started, int count) {}

  private final Map<String, Window> attempts = new LinkedHashMap<>(16, .75f, true);
  private final ObjectMapper mapper;
  private final LoginClientIpResolver clients;
  private final Clock clock = Clock.systemUTC();

  LoginRateLimitFilter(ObjectMapper mapper, LoginClientIpResolver clients) {
    this.mapper = mapper;
    this.clients = clients;
  }

  private synchronized boolean allow(String ip) {
    long now = clock.millis();
    Window w = attempts.get(ip);
    if (w == null || now - w.started() >= 60000) {
      if (attempts.size() >= 10000 && !attempts.containsKey(ip))
        attempts.remove(attempts.keySet().iterator().next());
      attempts.put(ip, new Window(now, 1));
      return true;
    }
    if (w.count() >= 30) return false;
    attempts.put(ip, new Window(w.started(), w.count() + 1));
    return true;
  }

  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    if (req.getMethod().equals("POST")
        && req.getRequestURI().equals("/api/v1/auth/login")
        && !allow(clients.resolve(req))) {
      res.setStatus(429);
      res.setHeader("Retry-After", "60");
      res.setContentType("application/problem+json");
      mapper.writeValue(res.getOutputStream(), Errors.body("RATE_LIMITED"));
      return;
    }
    chain.doFilter(req, res);
  }
}
