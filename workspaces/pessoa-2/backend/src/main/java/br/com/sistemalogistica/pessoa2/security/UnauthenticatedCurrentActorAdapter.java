package br.com.sistemalogistica.pessoa2.security;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import br.com.sistemalogistica.pessoa2.shared.contract.ContractException;

@Component
@Profile("!dev")
public class UnauthenticatedCurrentActorAdapter implements CurrentActorPort {

	@Override
	public ActorContext requireActor() {
		throw new ContractException("UNAUTHENTICATED", "Authentication is required.");
	}
}
