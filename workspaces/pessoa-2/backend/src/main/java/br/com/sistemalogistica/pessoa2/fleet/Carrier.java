package br.com.sistemalogistica.pessoa2.fleet;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "carriers")
public class Carrier {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(nullable = false, length = 160)
	private String name;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Carrier() {
	}

	public Carrier(String name, boolean active) {
		this(UUID.randomUUID(), name, active);
	}

	public Carrier(UUID id, String name, boolean active) {
		this.id = id;
		this.name = name;
		this.active = active;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public boolean isActive() {
		return active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void update(String name, boolean active) {
		this.name = name;
		this.active = active;
	}
}
