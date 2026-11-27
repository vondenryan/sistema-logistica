package logistics.identity.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LoginClientIpResolverTest {
  private MockHttpServletRequest request(String peer, String forwarded) {
    var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
    request.setRemoteAddr(peer);
    if (forwarded != null) request.addHeader("X-Real-IP", forwarded);
    request.addHeader("X-Forwarded-For", "198.51.100.55");
    request.addHeader("Forwarded", "for=198.51.100.55");
    return request;
  }

  @Test
  void onlyExplicitSocketPeersCanForwardOneClientAddress() {
    var resolver = new LoginClientIpResolver("172.30.64.10");
    assertEquals("203.0.113.9", resolver.resolve(request("172.30.64.10", "203.0.113.9")));
    assertEquals("192.0.2.1", resolver.resolve(request("192.0.2.1", "203.0.113.9")));
    assertEquals(
        "172.30.64.10", new LoginClientIpResolver("").resolve(request("172.30.64.10", "203.0.113.9")));
    var duplicate = request("172.30.64.10", "203.0.113.9");
    duplicate.addHeader("X-Real-IP", "203.0.113.10");
    assertEquals("172.30.64.10", resolver.resolve(duplicate));
    assertEquals("172.30.64.10", resolver.resolve(request("172.30.64.10", null)));
  }

  @Test
  void literalsAreCanonicalAndNeverResolveHostnames() {
    assertEquals(
        LoginClientIpResolver.normalize("2001:db8::1"),
        LoginClientIpResolver.normalize("2001:0DB8:0:0:0:0:0:1"));
    assertEquals("192.0.2.1", LoginClientIpResolver.normalize("::ffff:192.0.2.1"));
    assertEquals("192.0.2.1", LoginClientIpResolver.normalize("::ffff:c000:201"));
    var resolver = new LoginClientIpResolver("::ffff:172.30.64.10,2001:db8::1");
    assertEquals("192.0.2.1", resolver.resolve(request("172.30.64.10", "::ffff:192.0.2.1")));
    assertEquals("192.0.2.1", resolver.resolve(request("2001:0db8:0:0:0:0:0:1", "192.0.2.1")));
    for (String invalid :
        new String[] {
          "localhost",
          "127.1",
          "0177.0.0.1",
          "256.0.0.1",
          "203.0.113.1:80",
          "[::1]",
          "fe80::1%eth0",
          "1::2::3",
          ":1:2:3:4:5:6:7",
          "1:2:3:4:5:6:7:",
          "1:2:3:4:5:6:7:8:9",
          "203.0.113.1,203.0.113.2",
          " 203.0.113.1",
          ""
        }) {
      assertNull(LoginClientIpResolver.normalize(invalid), invalid);
      assertEquals("172.30.64.10", resolver.resolve(request("172.30.64.10", invalid)), invalid);
    }
    assertThrows(IllegalArgumentException.class, () -> new LoginClientIpResolver("localhost"));
  }

  private int status(LoginRateLimitFilter filter, MockHttpServletRequest request) throws Exception {
    var response = new MockHttpServletResponse();
    filter.doFilter(request, response, (req, res) -> res.getWriter().write("accepted"));
    if (response.getStatus() == 429) {
      assertEquals("60", response.getHeader("Retry-After"));
      assertEquals(
          "RATE_LIMITED",
          new ObjectMapper().readTree(response.getContentAsString()).get("code").asText());
    }
    return response.getStatus();
  }

  @Test
  void gatewayClientsHaveSeparateWindowsAndDirectSpoofCannotResetOne() throws Exception {
    var filter =
        new LoginRateLimitFilter(
            new ObjectMapper()
                .addMixIn(
                    org.springframework.http.ProblemDetail.class,
                    org.springframework.http.converter.json.ProblemDetailJacksonMixin.class),
            new LoginClientIpResolver("172.30.64.10"));
    for (int i = 0; i < 30; i++)
      assertEquals(200, status(filter, request("172.30.64.10", "192.0.2.1")));
    assertEquals(429, status(filter, request("172.30.64.10", "192.0.2.1")));
    assertEquals(200, status(filter, request("172.30.64.10", "192.0.2.2")));
    assertEquals(429, status(filter, request("192.0.2.1", "198.51.100.1")));
    assertEquals(429, status(filter, request("::ffff:192.0.2.1", "198.51.100.2")));
  }
}
