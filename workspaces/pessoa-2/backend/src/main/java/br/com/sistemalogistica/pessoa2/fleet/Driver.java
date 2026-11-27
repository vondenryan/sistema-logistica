package br.com.sistemalogistica.pessoa2.fleet;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "drivers")
public class Driver {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(nullable = false, length = 160)
	private String name;

	@Column(name = "carrier_id")
	private UUID carrierId;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Driver() {
	}

	public Driver(String name, UUID carrierId, boolean active) {
		this(UUID.randomUUID(), name, carrierId, active);
	}

	public Driver(UUID id, String name, UUID carrierId, boolean active) {
		this.id = id;
		this.name = name;
		this.carrierId = carrierId;
		this.active = active;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public UUID getCarrierId() {
		return carrierId;
	}

	public boolean isActive() {
		return active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void update(String name, UUID carrierId, boolean active) {
		this.name = name;
		this.carrierId = carrierId;
		this.active = active;
	}
}
