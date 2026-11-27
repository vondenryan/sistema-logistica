package br.com.sistemalogistica.pessoa2.origins;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OriginRepository extends JpaRepository<Origin, UUID> {
	Page<Origin> findAllByOrderByCreatedAtAscIdAsc(Pageable pageable);
}
