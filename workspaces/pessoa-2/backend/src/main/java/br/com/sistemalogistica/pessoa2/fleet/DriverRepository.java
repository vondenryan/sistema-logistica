package br.com.sistemalogistica.pessoa2.fleet;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRepository extends JpaRepository<Driver, UUID> {
	Page<Driver> findAllByOrderByCreatedAtAscIdAsc(Pageable pageable);

	java.util.List<Driver> findAllByActiveTrueOrderByIdAsc();
}
