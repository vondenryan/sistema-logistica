package logistics.identity.infrastructure;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter extends OncePerRequestFilter {
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String id = request.getHeader("X-Request-Id");
    try {
      id = UUID.fromString(id).toString();
    } catch (Exception ex) {
      id = UUID.randomUUID().toString();
    }
    response.setHeader("X-Request-Id", id);
    chain.doFilter(request, response);
  }
}
