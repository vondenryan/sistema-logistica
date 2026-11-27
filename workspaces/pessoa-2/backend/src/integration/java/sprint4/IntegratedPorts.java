package sprint4;

import br.com.sistemalogistica.pessoa2.security.ActorContext;
import br.com.sistemalogistica.pessoa2.security.Role;
import contracts.v1.CurrentActorPort;

final class IntegratedPorts implements br.com.sistemalogistica.pessoa2.security.CurrentActorPort {

    private final CurrentActorPort identity;

    IntegratedPorts(CurrentActorPort identity) {
        this.identity = identity;
    }

    @Override
    public ActorContext requireActor() {
        var actor = identity.requireActor();
        return new ActorContext(actor.actorId(), Role.valueOf(actor.role().name()), actor.storeId(), actor.driverId());
    }
}
