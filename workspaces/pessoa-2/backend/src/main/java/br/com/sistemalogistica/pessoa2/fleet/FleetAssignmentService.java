package br.com.sistemalogistica.pessoa2.fleet;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import contracts.v1.AssignmentRequest;
import contracts.v1.AssignmentSnapshot;
import contracts.v1.FleetAssignmentPort;
import contracts.v1.FleetReleasePort;
import contracts.v1.FleetType;
import contracts.v1.ReleaseReceipt;
import contracts.v1.ReleaseRequest;
import contracts.v1.RouteSnapshotPort;
import br.com.sistemalogistica.pessoa2.security.CurrentActorPort;
import contracts.v1.ContractException;

@Service
public class FleetAssignmentService implements FleetAssignmentPort, FleetReleasePort {

	private static final int MAX_RESERVATION_ATTEMPTS = 5;
	private static final String ROUTE_UNIQUE_INDEX = "ux_fleet_assignments_route_id";
	private static final String ACTIVE_DRIVER_INDEX = "ux_fleet_assignments_active_driver";
	private static final String ACTIVE_VEHICLE_INDEX = "ux_fleet_assignments_active_vehicle";
	private static final String RELEASE_EVENT_PRIMARY_KEY = "fleet_release_events_pkey";

	private final FleetAssignmentRepository assignmentRepository;
	private final FleetReleaseEventRepository releaseEventRepository;
	private final FleetAssignmentTransactionService transactionService;
	private final RouteSnapshotPort routeSnapshotPort;
	private final CurrentActorPort currentActorPort;

	public FleetAssignmentService(
			FleetAssignmentRepository assignmentRepository,
			FleetReleaseEventRepository releaseEventRepository,
			FleetAssignmentTransactionService transactionService,
			RouteSnapshotPort routeSnapshotPort,
			CurrentActorPort currentActorPort) {
		this.assignmentRepository = assignmentRepository;
		this.releaseEventRepository = releaseEventRepository;
		this.transactionService = transactionService;
		this.routeSnapshotPort = routeSnapshotPort;
		this.currentActorPort = currentActorPort;
	}

	@Override
	public AssignmentSnapshot assign(AssignmentRequest request) {
		validateAssignmentRequest(request);
		for (int attempt = 1; ; attempt++) {
			try {
				return transactionService.reserve(request);
			} catch (DataIntegrityViolationException exception) {
				if (!FleetSupport.isConstraintViolation(exception, ROUTE_UNIQUE_INDEX)
						&& !FleetSupport.isConstraintViolation(exception, ACTIVE_DRIVER_INDEX)
						&& !FleetSupport.isConstraintViolation(exception, ACTIVE_VEHICLE_INDEX)) {
					throw exception;
				}
				if (attempt == MAX_RESERVATION_ATTEMPTS) {
					throw new ContractException("CONFLICT");
				}
				// The failed reservation has rolled back. Select again in a fresh transaction.
			}
		}
	}

	@Override
	public ReleaseReceipt release(ReleaseRequest request) {
		validateReleaseRequest(request);
		try {
			return transactionService.release(request);
		} catch (DataIntegrityViolationException exception) {
			if (!FleetSupport.isConstraintViolation(exception, RELEASE_EVENT_PRIMARY_KEY)) {
				throw exception;
			}
			FleetReleaseEvent concurrentEvent = releaseEventRepository.findById(request.eventId()).orElse(null);
			if (concurrentEvent == null) {
				throw exception;
			}
			if (!concurrentEvent.getRouteId().equals(request.routeId())
					|| concurrentEvent.getReason() != request.reason()) {
				throw idempotencyConflict();
			}
			return new ReleaseReceipt(request.routeId(), false);
		}
	}

	@Transactional(readOnly = true)
	public AssignmentSnapshot get(UUID routeId) {
		FleetSupport.requireAdministrativeActor(currentActorPort);
		routeSnapshotPort.getById(routeId);
		FleetAssignment assignment = assignmentRepository.findByRouteId(routeId)
				.orElseThrow(FleetAssignmentService::notFound);
		return new AssignmentSnapshot(
				assignment.getAssignmentId(),
				assignment.getRouteId(),
				assignment.getDriverId(),
				assignment.getVehicleId(),
				assignment.getCarrierId(),
				assignment.getFleetType());
	}

	private static void validateAssignmentRequest(AssignmentRequest request) {
		if (request == null) {
			throw validation("request", "MUST_NOT_BE_NULL");
		}
		if (request.routeId() == null) {
			throw validation("routeId", "MUST_NOT_BE_NULL");
		}
		if (request.originId() == null) {
			throw validation("originId", "MUST_NOT_BE_NULL");
		}
		BigDecimal capacity = request.requiredCapacityKg();
		if (capacity == null || capacity.signum() <= 0) {
			throw validation("requiredCapacityKg", "MUST_BE_POSITIVE");
		}
		if (capacity.scale() > 3 || capacity.precision() - capacity.scale() > 9) {
			throw validation(
					"requiredCapacityKg",
					"MUST_BE_IN_RANGE");
		}
	}

	private static ContractException validation(String field, String reason) {
		return new ContractException("VALIDATION_ERROR", List.of(Map.of("field", field, "reason", reason)));
	}

	private static void validateReleaseRequest(ReleaseRequest request) {
		if (request == null) {
			throw validation("request", "MUST_NOT_BE_NULL");
		}
		if (request.routeId() == null) {
			throw validation("routeId", "MUST_NOT_BE_NULL");
		}
		if (request.eventId() == null) {
			throw validation("eventId", "MUST_NOT_BE_NULL");
		}
		if (request.reason() == null) {
			throw validation("reason", "MUST_NOT_BE_NULL");
		}
	}

	private static ContractException idempotencyConflict() {
		return new ContractException("IDEMPOTENCY_CONFLICT");
	}

	private static ContractException notFound() {
		return new ContractException("NOT_FOUND");
	}

}
