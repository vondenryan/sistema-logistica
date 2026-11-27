package br.com.sistemalogistica.pessoa2.suppliers;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
	Page<Supplier> findAllByOrderByCreatedAtAscIdAsc(Pageable pageable);
}
