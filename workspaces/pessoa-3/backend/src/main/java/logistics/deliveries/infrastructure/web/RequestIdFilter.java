package logistics.deliveries.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
  public static final String REQUEST_ID_ATTR = "X-Request-Id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String id = request.getHeader("X-Request-Id");
    try {
      if (id != null && !id.trim().isEmpty()) {
        id = UUID.fromString(id.trim()).toString();
      } else {
        id = UUID.randomUUID().toString();
      }
    } catch (Exception ex) {
      id = UUID.randomUUID().toString();
    }

    request.setAttribute(REQUEST_ID_ATTR, id);
    response.setHeader("X-Request-Id", id);
    chain.doFilter(request, response);
  }
}
