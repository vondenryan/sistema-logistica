package br.com.sistemalogistica.pessoa2.fleet;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import contracts.v1.ReleaseReason;

@Entity
@Table(name = "fleet_release_events")
public class FleetReleaseEvent {

	@Id
	@Column(name = "event_id", nullable = false, updatable = false)
	private UUID eventId;

	@Column(name = "route_id", nullable = false, updatable = false)
	private UUID routeId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32, updatable = false)
	private ReleaseReason reason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected FleetReleaseEvent() {
	}

	public FleetReleaseEvent(UUID eventId, UUID routeId, ReleaseReason reason) {
		this.eventId = eventId;
		this.routeId = routeId;
		this.reason = reason;
		this.createdAt = Instant.now();
	}

	public UUID getEventId() {
		return eventId;
	}

	public UUID getRouteId() {
		return routeId;
	}

	public ReleaseReason getReason() {
		return reason;
	}
}
