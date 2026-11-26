package br.com.sistemalogistica.pessoa2.fleet;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
	Page<Vehicle> findAllByOrderByCreatedAtAscIdAsc(Pageable pageable);

	boolean existsByIdentifier(String identifier);

	boolean existsByIdentifierAndIdNot(String identifier, UUID id);
}
