package logistics.deliveries.infrastructure.web;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {
  private MockHttpServletRequest request(String peer, String forwarded) {
    var req = new MockHttpServletRequest();
    req.setRemoteAddr(peer);
    if (forwarded != null) req.addHeader("X-Forwarded-For", forwarded);
    return req;
  }

  @Test
  void defaultsToSocketPeerAndTrustsOnlyExactConfiguredAddresses() {
    assertEquals(
        "203.0.113.1", new ClientIpResolver("").resolve(request("203.0.113.1", "192.0.2.1")));
    var resolver = new ClientIpResolver("192.0.2.10");
    assertEquals("203.0.113.1", resolver.resolve(request("203.0.113.1", "192.0.2.1")));
    assertEquals("203.0.113.1", resolver.resolve(request("192.0.2.10", "203.0.113.1")));
    assertEquals("192.0.2.10", resolver.resolve(request("192.0.2.10", null)));
    assertThrows(IllegalArgumentException.class, () -> new ClientIpResolver("localhost"));
  }

  @Test
  void invalidOrDuplicateForwardingHeadersFallBackToSocketPeer() {
    var resolver = new ClientIpResolver("192.0.2.10");
    for (String value :
        new String[] {
          "localhost",
          "203.0.113.1, 203.0.113.2",
          "203.0.113.1:80",
          "[::1]",
          "fe80::1%eth0",
          "1::2::3",
          "256.0.0.1",
          "0177.0.0.1",
          " 203.0.113.1",
          ""
        }) {
      assertEquals("192.0.2.10", resolver.resolve(request("192.0.2.10", value)), value);
    }
    var duplicate = request("192.0.2.10", "203.0.113.1");
    duplicate.addHeader("X-Forwarded-For", "203.0.113.2");
    assertEquals("192.0.2.10", resolver.resolve(duplicate));
  }

  @Test
  void equivalentIpv6AndMappedIpv4ShareOneKey() {
    var resolver = new ClientIpResolver("::ffff:192.0.2.10,2001:db8::1");
    assertEquals("203.0.113.1", resolver.resolve(request("192.0.2.10", "::ffff:203.0.113.1")));
    assertEquals(
        ClientIpResolver.normalize("2001:db8::2"),
        resolver.resolve(request("2001:0db8:0:0:0:0:0:1", "2001:0DB8:0:0:0:0:0:2")));
    assertEquals("192.0.2.10", ClientIpResolver.normalize("::ffff:c000:20a"));
  }
}
