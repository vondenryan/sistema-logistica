package br.com.sistemalogistica.pessoa2.fleet;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.sistemalogistica.pessoa2.contracts.v1.AssignmentRequest;
import br.com.sistemalogistica.pessoa2.contracts.v1.AssignmentSnapshot;
import br.com.sistemalogistica.pessoa2.contracts.v1.FleetAssignmentPort;
import br.com.sistemalogistica.pessoa2.contracts.v1.FleetReleasePort;
import br.com.sistemalogistica.pessoa2.contracts.v1.FleetType;
import br.com.sistemalogistica.pessoa2.contracts.v1.ReleaseReceipt;
import br.com.sistemalogistica.pessoa2.contracts.v1.ReleaseRequest;
import br.com.sistemalogistica.pessoa2.contracts.v1.RouteSnapshotPort;
import br.com.sistemalogistica.pessoa2.security.CurrentActorPort;
import br.com.sistemalogistica.pessoa2.shared.contract.ContractException;

@Service
public class FleetAssignmentService implements FleetAssignmentPort, FleetReleasePort {

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
		FleetAssignment previous = assignmentRepository.findByRouteId(request.routeId()).orElse(null);
		if (previous != null) {
			return idempotentResult(previous, request);
		}

		try {
			return transactionService.reserve(request);
		} catch (DataIntegrityViolationException exception) {
			FleetAssignment concurrentResult = assignmentRepository.findByRouteId(request.routeId()).orElse(null);
			if (concurrentResult != null) {
				return idempotentResult(concurrentResult, request);
			}
			if (FleetSupport.isConstraintViolation(exception, ACTIVE_DRIVER_INDEX)
					|| FleetSupport.isConstraintViolation(exception, ACTIVE_VEHICLE_INDEX)) {
				throw noEligible();
			}
			if (FleetSupport.isConstraintViolation(exception, ROUTE_UNIQUE_INDEX)) {
				throw new ContractException("CONFLICT", "The route already has a fleet assignment.");
			}
			throw exception;
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
			throw ContractException.validation("request", "Must not be null.");
		}
		if (request.routeId() == null) {
			throw ContractException.validation("routeId", "Must not be null.");
		}
		if (request.originId() == null) {
			throw ContractException.validation("originId", "Must not be null.");
		}
		BigDecimal capacity = request.requiredCapacityKg();
		if (capacity == null || capacity.signum() <= 0) {
			throw ContractException.validation("requiredCapacityKg", "Must be greater than zero.");
		}
		if (capacity.scale() > 3 || capacity.precision() - capacity.scale() > 9) {
			throw ContractException.validation(
					"requiredCapacityKg",
					"Must fit decimal(12,3).");
		}
	}

	private static void validateReleaseRequest(ReleaseRequest request) {
		if (request == null) {
			throw ContractException.validation("request", "Must not be null.");
		}
		if (request.routeId() == null) {
			throw ContractException.validation("routeId", "Must not be null.");
		}
		if (request.eventId() == null) {
			throw ContractException.validation("eventId", "Must not be null.");
		}
		if (request.reason() == null) {
			throw ContractException.validation("reason", "Must not be null.");
		}
	}

	private static AssignmentSnapshot idempotentResult(FleetAssignment assignment, AssignmentRequest request) {
		if (!assignment.getOriginId().equals(request.originId())
				|| assignment.getRequiredCapacityKg().compareTo(request.requiredCapacityKg()) != 0) {
			throw idempotencyConflict();
		}
		return new AssignmentSnapshot(
				assignment.getAssignmentId(),
				assignment.getRouteId(),
				assignment.getDriverId(),
				assignment.getVehicleId(),
				assignment.getCarrierId(),
				assignment.getFleetType());
	}

	private static ContractException noEligible() {
		return new ContractException("NO_ELIGIBLE_RESOURCE", "No eligible driver and vehicle are available.");
	}

	private static ContractException idempotencyConflict() {
		return new ContractException("IDEMPOTENCY_CONFLICT", "The route was already assigned with a different request.");
	}

	private static ContractException notFound() {
		return new ContractException("NOT_FOUND", "Fleet assignment was not found.");
	}

}
