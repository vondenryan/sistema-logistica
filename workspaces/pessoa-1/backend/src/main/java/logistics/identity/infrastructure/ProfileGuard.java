package logistics.identity.infrastructure;

import contracts.v1.*;
import java.util.Arrays;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
class ProfileGuard {
  @Bean
  static BeanFactoryPostProcessor validateProfiles(Environment env) {
    return factory -> {
      if (!env.matchesProfiles("prod | integration")) return;
      if (env.matchesProfiles("dev | test"))
        throw new IllegalStateException("dev/test fake adapters cannot run with prod/integration");
      if ("fake".equals(env.getProperty("ROUTING_PROVIDER", "osrm")))
        throw new IllegalStateException(
            "fake routing requires dev/test profile and cannot run with prod/integration");
      var missing =
          Arrays.stream(
                  new Class<?>[] {
                    PartnerLookupPort.class,
                    FleetAssignmentPort.class,
                    FleetReleasePort.class,
                    DeliveryCommandPort.class,
                    DeliveryStatusPort.class,
                    NotificationRequestPort.class,
                    DocumentRequestPort.class
                  })
              .filter(type -> factory.getBeanNamesForType(type, true, false).length == 0)
              .map(Class::getSimpleName)
              .toList();
      if (!missing.isEmpty())
        throw new IllegalStateException(
            "Missing P2/P3 port providers for prod/integration: " + String.join(", ", missing));
    };
  }
}
