package br.com.sistemalogistica.pessoa2.fleet;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "vehicles")
public class Vehicle {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(nullable = false, length = 40)
	private String identifier;

	@Column(name = "capacity_kg", nullable = false, precision = 12, scale = 3)
	private BigDecimal capacityKg;

	@Enumerated(EnumType.STRING)
	@Column(name = "fleet_type", nullable = false, length = 16)
	private FleetType fleetType;

	@Column(name = "carrier_id")
	private UUID carrierId;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Vehicle() {
	}

	public Vehicle(
			String identifier,
			BigDecimal capacityKg,
			FleetType fleetType,
			UUID carrierId,
			boolean active) {
		this(UUID.randomUUID(), identifier, capacityKg, fleetType, carrierId, active);
	}

	public Vehicle(
			UUID id,
			String identifier,
			BigDecimal capacityKg,
			FleetType fleetType,
			UUID carrierId,
			boolean active) {
		this.id = id;
		this.identifier = identifier;
		this.capacityKg = capacityKg;
		this.fleetType = fleetType;
		this.carrierId = carrierId;
		this.active = active;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getIdentifier() {
		return identifier;
	}

	public BigDecimal getCapacityKg() {
		return capacityKg;
	}

	public FleetType getFleetType() {
		return fleetType;
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

	public void update(
			String identifier,
			BigDecimal capacityKg,
			FleetType fleetType,
			UUID carrierId,
			boolean active) {
		this.identifier = identifier;
		this.capacityKg = capacityKg;
		this.fleetType = fleetType;
		this.carrierId = carrierId;
		this.active = active;
	}
}
