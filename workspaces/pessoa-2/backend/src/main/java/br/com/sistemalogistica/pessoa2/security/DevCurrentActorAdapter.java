package br.com.sistemalogistica.pessoa2.security;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevCurrentActorAdapter implements CurrentActorPort {

	private final ActorContext actor;

	public DevCurrentActorAdapter(
			@Value("${pessoa2.dev-actor.actor-id}") UUID actorId,
			@Value("${pessoa2.dev-actor.role}") Role role,
			@Value("${pessoa2.dev-actor.store-id:}") String storeId,
			@Value("${pessoa2.dev-actor.driver-id:}") String driverId) {
		this.actor = new ActorContext(actorId, role, parseUuid(storeId), parseUuid(driverId));
	}

	@Override
	public ActorContext requireActor() {
		return actor;
	}

	private UUID parseUuid(String value) {
		return value == null || value.isBlank() ? null : UUID.fromString(value);
	}
}
