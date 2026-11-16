package logistics.billing.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import contracts.v1.*;
import java.math.BigDecimal;
import java.util.UUID;
import logistics.geography.application.RoutingProvider;
import org.junit.jupiter.api.Test;

class QuoteServiceTest {
  @Test
  void quoteIsRoundedAndStableWithoutTariffOrProvider() {
    UUID id = UUID.randomUUID();
    var store = mock(QuoteStore.class);
    var orders = mock(OrderSnapshotPort.class);
    var actor = mock(CurrentActorPort.class);
    var routing = mock(RoutingProvider.class);
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
                "Recipient",
                null,
                "code",
                new BigDecimal("1.000"),
                null,
                "BRL"));
    when(routing.roadDistanceKm(any(), any())).thenReturn(new BigDecimal("12.345"));
    // Null coordinates are acceptable here because the provider itself is mocked.
    when(routing.roadDistanceKm(isNull(), isNull())).thenReturn(new BigDecimal("12.345"));
    when(routing.source()).thenReturn("FAKE");
    when(store.save(any())).thenAnswer(a -> a.getArgument(0));
    var first = new QuoteService(store, orders, actor, routing, "2.50").quote(id);
    assertThat(first.amount()).isEqualByComparingTo("30.86");
    when(store.latestQuote(id)).thenReturn(first);
    reset(routing);
    assertThat(new QuoteService(store, orders, actor, routing, null).quote(id)).isEqualTo(first);
    verifyNoInteractions(routing);
  }
}
