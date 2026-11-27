package contracts.v1;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.UUID;

/** The same behavioral checks run against the fake and PostgreSQL adapters. */
public final class PartnerLookupContract {
  private PartnerLookupContract() {}

  public static void verify(
      PartnerLookupPort port, UUID activeId, UUID inactiveId, UUID storeId, UUID missingId) {
    OriginSnapshot active = port.getOrigin(activeId);
    OriginSnapshot inactive = port.getOrigin(inactiveId);
    assertEquals(activeId, active.id());
    assertEquals(inactiveId, inactive.id());
    assertTrue(active.active());
    assertFalse(inactive.active());
    for (OriginSnapshot origin : new OriginSnapshot[] {active, inactive}) {
      assertEquals(storeId, origin.storeId());
      assertEquals(storeId, port.getStore(origin.storeId()).id());
      assertEquals(0, new BigDecimal("-26.48").compareTo(origin.address().latitude()));
      assertEquals(0, new BigDecimal("-49.07").compareTo(origin.address().longitude()));
    }
    assertEquals(
        "NOT_FOUND", assertThrows(ContractException.class, () -> port.getOrigin(missingId)).code());
    assertEquals(
        "NOT_FOUND", assertThrows(ContractException.class, () -> port.getStore(missingId)).code());
    assertEquals(
        "VALIDATION_ERROR",
        assertThrows(ContractException.class, () -> port.getOrigin(null)).code());
    assertEquals(
        "VALIDATION_ERROR",
        assertThrows(ContractException.class, () -> port.getStore(null)).code());
  }
}
