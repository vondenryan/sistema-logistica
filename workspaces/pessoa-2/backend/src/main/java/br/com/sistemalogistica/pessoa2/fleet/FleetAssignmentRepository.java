package br.com.sistemalogistica.pessoa2.fleet;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface FleetAssignmentRepository extends JpaRepository<FleetAssignment, UUID> {

	Optional<FleetAssignment> findByRouteId(UUID routeId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select assignment from FleetAssignment assignment where assignment.routeId = :routeId")
	Optional<FleetAssignment> findByRouteIdForUpdate(@Param("routeId") UUID routeId);

	List<FleetAssignment> findAllByReleasedAtIsNull();

	long countByDriverIdAndReleasedAtIsNull(UUID driverId);

	@Transactional
	void deleteAllByRouteIdIn(Iterable<UUID> routeIds);
}
