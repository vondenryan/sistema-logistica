package br.com.sistemalogistica.pessoa2.security;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import br.com.sistemalogistica.pessoa2.shared.contract.ContractException;

@Component
@Profile("dev")
public class DevCurrentActorAdapter implements CurrentActorPort {

	private static final String ROLE_HEADER = "X-Dev-Role";
	private static final String STORE_ID_HEADER = "X-Dev-Store-Id";

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
		if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
			return actor;
		}

		HttpServletRequest request = attributes.getRequest();
		String roleHeader = request.getHeader(ROLE_HEADER);
		String storeIdHeader = request.getHeader(STORE_ID_HEADER);
		if (roleHeader == null && storeIdHeader == null) {
			return actor;
		}

		Role role = roleHeader == null ? actor.role() : parseRole(roleHeader);
		UUID storeId = storeIdHeader == null ? actor.storeId() : parseHeaderUuid(storeIdHeader);
		if (role == Role.STORE && storeId == null) {
			throw unauthenticated();
		}
		if (role != Role.STORE && storeIdHeader != null) {
			throw unauthenticated();
		}

		return new ActorContext(actor.actorId(), role, role == Role.STORE ? storeId : null, null);
	}

	private UUID parseUuid(String value) {
		return value == null || value.isBlank() ? null : UUID.fromString(value);
	}

	private Role parseRole(String value) {
		return switch (value) {
			case "ADMIN" -> Role.ADMIN;
			case "OPERATOR" -> Role.OPERATOR;
			case "STORE" -> Role.STORE;
			default -> throw unauthenticated();
		};
	}

	private UUID parseHeaderUuid(String value) {
		try {
			UUID parsed = UUID.fromString(value);
			if (!parsed.toString().equalsIgnoreCase(value)) {
				throw unauthenticated();
			}
			return parsed;
		} catch (IllegalArgumentException exception) {
			throw unauthenticated();
		}
	}

	private static ContractException unauthenticated() {
		return new ContractException("UNAUTHENTICATED", "The development actor headers are invalid.");
	}
}
