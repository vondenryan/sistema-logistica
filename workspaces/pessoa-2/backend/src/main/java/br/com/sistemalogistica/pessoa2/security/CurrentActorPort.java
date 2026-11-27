package br.com.sistemalogistica.pessoa2.security;

public interface CurrentActorPort {
	ActorContext requireActor();
}
