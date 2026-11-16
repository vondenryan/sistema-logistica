package logistics.billing.infrastructure;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import contracts.v1.*;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import logistics.billing.application.*;
import logistics.geography.application.RoutingProvider;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class QuoteConcurrencyTest {
  @Test
  void concurrentQuotesReturnOneStoredSnapshot() throws Exception {
    JdbcDataSource source = new JdbcDataSource();
    source.setURL("jdbc:h2:mem:quote_race;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    var db = new JdbcTemplate(source);
    db.execute("CREATE SCHEMA billing");
    db.execute(
        "CREATE TABLE billing.freight_quotes(id UUID PRIMARY KEY,order_id UUID UNIQUE,distance_km"
            + " NUMERIC(12,3),tariff_per_km NUMERIC(12,2),amount NUMERIC(12,2),calculated_at"
            + " TIMESTAMP WITH TIME ZONE,source VARCHAR(20))");
    var store = new QuoteAdapter(db);
    var orders = mock(OrderSnapshotPort.class);
    var actor = mock(CurrentActorPort.class);
    var provider = mock(RoutingProvider.class);
    UUID id = UUID.randomUUID();
    when(actor.requireActor())
        .thenReturn(new ActorContext(UUID.randomUUID(), Role.ADMIN, null, null));
    when(orders.getById(id))
        .thenReturn(
            new OrderSnapshot(
                id,
                UUID.randomUUID(),
                UUID.randomUUID(),
                OrderStatus.CONFIRMED,
                null,
                null,
                "x",
                null,
                "code",
                BigDecimal.ONE,
                null,
                "BRL"));
    var gate = new CyclicBarrier(2);
    when(provider.roadDistanceKm(isNull(), isNull()))
        .thenAnswer(
            a -> {
              gate.await(5, TimeUnit.SECONDS);
              return new BigDecimal("1.005");
            });
    when(provider.source()).thenReturn("FAKE");
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a = pool.submit(() -> new QuoteService(store, orders, actor, provider, "2.50").quote(id));
      var b = pool.submit(() -> new QuoteService(store, orders, actor, provider, "3.50").quote(id));
      assertThat(a.get(10, TimeUnit.SECONDS)).isEqualTo(b.get(10, TimeUnit.SECONDS));
    }
    assertThat(db.queryForObject("SELECT COUNT(*) FROM billing.freight_quotes", Integer.class))
        .isEqualTo(1);
  }
}
