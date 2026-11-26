package br.com.sistemalogistica.pessoa2.fleet;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface FleetReleaseEventRepository extends JpaRepository<FleetReleaseEvent, UUID> {

	@Transactional
	void deleteAllByRouteIdIn(Iterable<UUID> routeIds);
}
