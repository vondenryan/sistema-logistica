package logistics.geography.infrastructure;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import contracts.v1.*;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class OsrmRoutingProviderTest {
  @Test
  void convertsRoadDistanceAndRejectsFailures() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var status = new java.util.concurrent.atomic.AtomicInteger(200);
    var oversized = new java.util.concurrent.atomic.AtomicBoolean(false);
    var malformed = new java.util.concurrent.atomic.AtomicBoolean(false);
    server.createContext(
        "/route/v1/driving/",
        e -> {
          byte[] body =
              (oversized.get()
                      ? "x".repeat(100001)
                      : malformed.get()
                          ? "not-json"
                          : "{\"code\":\"Ok\",\"routes\":[{\"distance\":12345}]}")
                  .getBytes(StandardCharsets.UTF_8);
          e.sendResponseHeaders(status.get(), body.length);
          try (var out = e.getResponseBody()) {
            out.write(body);
          }
        });
    server.start();
    try {
      var p =
          new OsrmRoutingProvider(
              "http://127.0.0.1:" + server.getAddress().getPort(), new ObjectMapper());
      var address =
          new AddressSnapshot(
              "Rua",
              null,
              "Cidade",
              "SC",
              null,
              "BR",
              new BigDecimal("-26"),
              new BigDecimal("-49"));
      assertThat(p.roadDistanceKm(address, address)).isEqualByComparingTo("12.345");
      malformed.set(true);
      assertThatThrownBy(() -> p.roadDistanceKm(address, address))
          .isInstanceOf(ContractException.class)
          .hasMessage("UPSTREAM_ERROR");
      malformed.set(false);
      oversized.set(true);
      assertThatThrownBy(() -> p.roadDistanceKm(address, address))
          .isInstanceOf(ContractException.class)
          .hasMessage("UPSTREAM_ERROR");
      oversized.set(false);
      status.set(503);
      assertThatThrownBy(() -> p.roadDistanceKm(address, address))
          .isInstanceOf(ContractException.class)
          .hasMessage("DEPENDENCY_UNAVAILABLE");
    } finally {
      server.stop(0);
    }
  }
}
