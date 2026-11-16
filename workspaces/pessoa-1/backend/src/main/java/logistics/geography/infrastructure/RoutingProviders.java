package logistics.geography.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import contracts.v1.*;
import java.math.*;
import java.net.*;
import java.net.http.*;
import logistics.geography.application.RoutingProvider;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration
public class RoutingProviders {
  @Bean
  RoutingProvider routingProvider(Environment env, ObjectMapper json) {
    String choice = env.getProperty("ROUTING_PROVIDER", "osrm");
    if (choice.equals("fake")) {
      if (!env.matchesProfiles("dev | test") || env.matchesProfiles("prod | integration"))
        throw new IllegalStateException("fake routing requires dev/test profile");
      return new RoutingProvider() {
        public BigDecimal roadDistanceKm(AddressSnapshot a, AddressSnapshot b) {
          check(a, b);
          return a.latitude()
              .subtract(b.latitude())
              .abs()
              .add(a.longitude().subtract(b.longitude()).abs())
              .multiply(new BigDecimal("100"))
              .setScale(3, RoundingMode.HALF_UP);
        }

        public String source() {
          return "FAKE";
        }
      };
    }
    if (!choice.equals("osrm")) throw new IllegalStateException("unknown routing provider");
    return new OsrmRoutingProvider(env.getProperty("OSRM_BASE_URL", "http://localhost:5000"), json);
  }

  static void check(AddressSnapshot a, AddressSnapshot b) {
    if (a == null
        || b == null
        || a.latitude() == null
        || a.longitude() == null
        || b.latitude() == null
        || b.longitude() == null) throw new ContractException("PRECONDITION_FAILED");
  }
}
