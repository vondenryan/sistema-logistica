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

import contracts.v1.AssignmentRequest;
import contracts.v1.FleetType;

@Entity
@Table(name = "fleet_assignments")
public class FleetAssignment {

	@Id
	@Column(name = "assignment_id", nullable = false, updatable = false)
	private UUID assignmentId;

	@Column(name = "route_id", nullable = false, updatable = false)
	private UUID routeId;

	@Column(name = "origin_id", nullable = false, updatable = false)
	private UUID originId;

	@Column(name = "required_capacity_kg", nullable = false, precision = 12, scale = 3, updatable = false)
	private BigDecimal requiredCapacityKg;

	@Column(name = "driver_id", nullable = false, updatable = false)
	private UUID driverId;

	@Column(name = "vehicle_id", nullable = false, updatable = false)
	private UUID vehicleId;

	@Column(name = "carrier_id", updatable = false)
	private UUID carrierId;

	@Enumerated(EnumType.STRING)
	@Column(name = "fleet_type", nullable = false, length = 16, updatable = false)
	private FleetType fleetType;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "released_at")
	private Instant releasedAt;

	protected FleetAssignment() {
	}

	public FleetAssignment(
			UUID assignmentId,
			AssignmentRequest request,
			UUID driverId,
			UUID vehicleId,
			UUID carrierId,
			FleetType fleetType) {
		this.assignmentId = assignmentId;
		this.routeId = request.routeId();
		this.originId = request.originId();
		this.requiredCapacityKg = request.requiredCapacityKg();
		this.driverId = driverId;
		this.vehicleId = vehicleId;
		this.carrierId = carrierId;
		this.fleetType = fleetType;
		this.createdAt = Instant.now();
	}

	public UUID getAssignmentId() {
		return assignmentId;
	}

	public UUID getRouteId() {
		return routeId;
	}

	public UUID getOriginId() {
		return originId;
	}

	public BigDecimal getRequiredCapacityKg() {
		return requiredCapacityKg;
	}

	public UUID getDriverId() {
		return driverId;
	}

	public UUID getVehicleId() {
		return vehicleId;
	}

	public UUID getCarrierId() {
		return carrierId;
	}

	public FleetType getFleetType() {
		return fleetType;
	}

	public Instant getReleasedAt() {
		return releasedAt;
	}

	public void release(Instant releasedAt) {
		this.releasedAt = releasedAt;
	}
}
