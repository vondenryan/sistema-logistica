package br.com.sistemalogistica.pessoa2.fleet;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarrierRepository extends JpaRepository<Carrier, UUID> {
	Page<Carrier> findAllByOrderByCreatedAtAscIdAsc(Pageable pageable);

	boolean existsByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
