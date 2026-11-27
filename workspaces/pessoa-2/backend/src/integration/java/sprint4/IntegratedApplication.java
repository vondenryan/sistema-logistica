package sprint4;

import br.com.sistemalogistica.pessoa2.Pessoa2BackendApplication;
<<<<<<< HEAD
=======
import br.com.sistemalogistica.pessoa2.security.ActorContext;
import br.com.sistemalogistica.pessoa2.security.Role;
>>>>>>> origin/feat/p2-sprint-4
import br.com.sistemalogistica.pessoa2.fleet.RouteCapacityValidator;
import contracts.v1.ContractException;
import contracts.v1.CurrentActorPort;
import contracts.v1.OrderSnapshot;
import contracts.v1.OrderSnapshotPort;
import contracts.v1.OrderStatus;
import contracts.v1.RouteSnapshotPort;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import logistics.orders.application.OrderService;
import logistics.routing.application.RouteService;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootConfiguration
@EnableAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
@EntityScan(basePackageClasses = Pessoa2BackendApplication.class)
@EnableJpaRepositories(basePackageClasses = Pessoa2BackendApplication.class)
@ComponentScan(basePackages = {"logistics", "br.com.sistemalogistica.pessoa2"},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = Pessoa2BackendApplication.class),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = {
            "logistics\\.fakes\\..*",
            "logistics\\.deliveries\\.infrastructure\\.web\\.(JsonConfig|Errors|RequestIdFilter)"
        })
    })
public class IntegratedApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(IntegratedApplication.class);
        app.setAdditionalProfiles("integration");
        app.run(args);
    }

    @Bean
    @Order(1)
    SecurityFilterChain publicTracking(HttpSecurity http) throws Exception {
        return http
            .securityMatcher(PathPatternRequestMatcher.withDefaults()
                .matcher(HttpMethod.GET, "/api/v1/tracking/{code}"))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
            .build();
    }

    @Bean
    FlywayMigrationStrategy moduleMigrations(DataSource database) {
        return core -> {
            core.migrate();
            migrate(database, "pessoa2", List.of("classpath:db/migration"));
            migrate(database, "deliveries", List.of(
                "classpath:core/db/migration/deliveries",
                "classpath:core/db/migration/notifications",
                "classpath:core/db/migration/documents"));
        };
    }

    private static void migrate(DataSource database, String schema, List<String> locations) {
        Flyway.configure()
            .dataSource(database)
            .defaultSchema(schema)
            .locations(locations.toArray(String[]::new))
            .validateMigrationNaming(true)
            .load()
            .migrate();
    }

    // Resolve the producer only when a port is called. Orders and routing both call
    // deliveries, whose commands in turn read order and route snapshots.
    @Bean
    @Primary
    OrderSnapshotPort orderLookup(ObjectProvider<OrderService> producer) {
        return new OrderSnapshotPort() {
            @Override
            public OrderSnapshot getById(UUID id) {
                return producer.getObject().getById(id);
            }

            @Override
            public OrderSnapshot findByTrackingCode(String trackingCode) {
                return producer.getObject().findByTrackingCode(trackingCode);
            }
        };
    }

    @Bean
    @Primary
    RouteSnapshotPort routeLookup(ObjectProvider<RouteService> producer) {
        return id -> producer.getObject().getById(id);
    }

    @Bean
    RouteCapacityValidator confirmedOrderCapacity(OrderSnapshotPort orders) {
        return (route, requestedCapacity) -> {
            BigDecimal totalCapacity = BigDecimal.ZERO;
            for (UUID orderId : route.orderIds()) {
                OrderSnapshot order = orders.getById(orderId);
                if (order.status() != OrderStatus.CONFIRMED || !route.storeId().equals(order.storeId())
                        || !route.originId().equals(order.originId())) {
                    throw new ContractException("PRECONDITION_FAILED");
                }
                totalCapacity = totalCapacity.add(order.totalWeightKg());
            }
            if (totalCapacity.compareTo(requestedCapacity) != 0) {
                throw new ContractException("PRECONDITION_FAILED");
            }
        };
    }

    @Bean
<<<<<<< HEAD
    IntegratedPorts integratedPorts(CurrentActorPort actor) {
        return new IntegratedPorts(actor);
=======
    br.com.sistemalogistica.pessoa2.security.CurrentActorPort partnerActor(CurrentActorPort identity) {
        return () -> {
            var actor = identity.requireActor();
            return new ActorContext(actor.actorId(), Role.valueOf(actor.role().name()), actor.storeId(), actor.driverId());
        };
>>>>>>> origin/feat/p2-sprint-4
    }
}
