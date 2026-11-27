package logistics.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import contracts.v1.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

class ProfileGuardTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(ProfileGuard.class);

  @Test
  void integrationDoesNotScanFixtures() {
    var env = new StandardEnvironment();
    env.setActiveProfiles("integration");
    var scanner = new ClassPathScanningCandidateComponentProvider(false, env);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
    Set<String> names =
        scanner.findCandidateComponents("logistics").stream()
            .map(definition -> definition.getBeanClassName())
            .collect(java.util.stream.Collectors.toSet());
    assertThat(names)
        .doesNotContain(
            Fixtures.class.getName(),
            FakeCurrentActorPort.class.getName(),
            "logistics.orders.infrastructure.DevFakeReplay",
            "logistics.routing.infrastructure.DevRouteReplay");
    env.setActiveProfiles("dev");
    assertThat(scanner.findCandidateComponents("logistics").stream().map(d -> d.getBeanClassName()))
        .contains(Fixtures.class.getName());
  }

  @Test
  void integrationReportsMissingRealPortsBeforeInstantiation() {
    runner
        .withPropertyValues("spring.profiles.active=integration")
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .hasMessage(
                        "Missing P2/P3 port providers for prod/integration: PartnerLookupPort,"
                            + " FleetAssignmentPort, FleetReleasePort, DeliveryCommandPort,"
                            + " DeliveryStatusPort, NotificationRequestPort, DocumentRequestPort"));
  }

  @Test
  void partialPortInventoryStillFailsBeforeProvidersAreInstantiated() {
    for (String profile : new String[] {"integration", "prod"}) {
      runner
          .withPropertyValues("spring.profiles.active=" + profile)
          .withBean(
              PartnerLookupPort.class,
              () -> {
                throw new AssertionError("instantiated");
              })
          .withBean(
              FleetAssignmentPort.class,
              () -> {
                throw new AssertionError("instantiated");
              })
          .withBean(
              DeliveryCommandPort.class,
              () -> {
                throw new AssertionError("instantiated");
              })
          .withBean(
              NotificationRequestPort.class,
              () -> {
                throw new AssertionError("instantiated");
              })
          .run(
              context ->
                  assertThat(context.getStartupFailure())
                      .hasMessage(
                          "Missing P2/P3 port providers for prod/integration: FleetReleasePort,"
                              + " DeliveryStatusPort, DocumentRequestPort"));
    }
  }

  @Test
  void mixedProfilesAreRejectedBeforeMissingDependencies() {
    runner
        .withPropertyValues("spring.profiles.active=dev,integration")
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .hasMessage("dev/test fake adapters cannot run with prod/integration"));
  }

  @Test
  void fakeRoutingIsRejectedInIntegrationAndProd() {
    for (String profile : new String[] {"integration", "prod"}) {
      runner
          .withPropertyValues("spring.profiles.active=" + profile, "ROUTING_PROVIDER=fake")
          .run(
              context ->
                  assertThat(context.getStartupFailure())
                      .hasMessage(
                          "fake routing requires dev/test profile and cannot run with"
                              + " prod/integration"));
    }
  }
}
