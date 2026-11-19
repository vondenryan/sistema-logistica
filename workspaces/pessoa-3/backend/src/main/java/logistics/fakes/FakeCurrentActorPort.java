package logistics.fakes;

import contracts.v1.ActorContext;
import contracts.v1.ContractException;
import contracts.v1.CurrentActorPort;
import contracts.v1.Role;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class FakeCurrentActorPort implements CurrentActorPort {
  public static final UUID DEFAULT_DRIVER_ID =
      UUID.fromString("55555555-5555-4555-8555-555555555555");
  public static final UUID DEFAULT_OPERATOR_ID =
      UUID.fromString("aaaaaaaa-1111-4aaa-8aaa-aaaaaaaaaaaa");
  public static final UUID DEFAULT_ADMIN_ID =
      UUID.fromString("bbbbbbbb-2222-4bbb-8bbb-bbbbbbbbbbbb");
  public static final UUID DEFAULT_STORE_ID =
      UUID.fromString("22222222-2222-4222-8222-222222222222");

  private final ThreadLocal<ActorContext> currentActor =
      ThreadLocal.withInitial(
          () -> new ActorContext(DEFAULT_DRIVER_ID, Role.DRIVER, null, DEFAULT_DRIVER_ID));

  @Override
  public ActorContext requireActor() {
    ActorContext actor = currentActor.get();
    if (actor == null) {
      throw new ContractException("UNAUTHENTICATED");
    }
    return actor;
  }

  public void setActor(ActorContext actor) {
    currentActor.set(actor);
  }

  public void asDriver(UUID driverId) {
    currentActor.set(new ActorContext(driverId, Role.DRIVER, null, driverId));
  }

  public void asDefaultDriver() {
    asDriver(DEFAULT_DRIVER_ID);
  }

  public void asOperator() {
    currentActor.set(new ActorContext(DEFAULT_OPERATOR_ID, Role.OPERATOR, null, null));
  }

  public void asAdmin() {
    currentActor.set(new ActorContext(DEFAULT_ADMIN_ID, Role.ADMIN, null, null));
  }

  public void asStore(UUID storeId) {
    currentActor.set(new ActorContext(UUID.randomUUID(), Role.STORE, storeId, null));
  }

  public void unauthenticated() {
    currentActor.set(null);
  }

  public void reset() {
    asDefaultDriver();
  }
}
