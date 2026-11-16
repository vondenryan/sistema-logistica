package logistics.identity.infrastructure;

import contracts.v1.*;

/** Explicit test double; never registered as a Spring bean. */
public final class FakeCurrentActorPort implements CurrentActorPort {
  private ActorContext actor;

  public FakeCurrentActorPort(ActorContext actor) {
    this.actor = actor;
  }

  public void setActor(ActorContext actor) {
    this.actor = actor;
  }

  public ActorContext requireActor() {
    if (actor == null) throw new ContractException("UNAUTHENTICATED");
    return actor;
  }
}
