package br.com.sistemalogistica.pessoa2.fleet;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import contracts.v1.AssignmentRequest;
import contracts.v1.AssignmentSnapshot;
import contracts.v1.FleetAssignmentPort;
import contracts.v1.FleetReleasePort;
import contracts.v1.ReleaseReceipt;
import contracts.v1.ReleaseRequest;
import contracts.v1.RouteSnapshotPort;
import br.com.sistemalogistica.pessoa2.security.CurrentActorPort;
import contracts.v1.ContractException;

@Service
public class FleetAssignmentService implements FleetAssignmentPort, FleetReleasePort {

	private final FleetAssignmentRepository assignmentRepository;
	private final FleetAssignmentTransactionService transactionService;
	private final RouteSnapshotPort routeSnapshotPort;
	private final CurrentActorPort currentActorPort;

	public FleetAssignmentService(
			FleetAssignmentRepository assignmentRepository,
			FleetAssignmentTransactionService transactionService,
			RouteSnapshotPort routeSnapshotPort,
			CurrentActorPort currentActorPort) {
		this.assignmentRepository = assignmentRepository;
		this.transactionService = transactionService;
		this.routeSnapshotPort = routeSnapshotPort;
		this.currentActorPort = currentActorPort;
	}

	@Override
	public AssignmentSnapshot assign(AssignmentRequest request) {
		validateAssignmentRequest(request);
		return transactionService.reserve(request);
	}

	@Override
	public ReleaseReceipt release(ReleaseRequest request) {
		validateReleaseRequest(request);
		return transactionService.release(request);
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

	private static ContractException validation(String field, String reason) {
		return new ContractException("VALIDATION_ERROR",
				List.of(Map.of("field", field, "reason", reason)));
	}

	private static ContractException notFound() {
		return new ContractException("NOT_FOUND");
	}

}
