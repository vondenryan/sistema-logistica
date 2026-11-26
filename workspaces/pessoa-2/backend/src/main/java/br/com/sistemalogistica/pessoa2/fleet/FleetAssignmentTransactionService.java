package br.com.sistemalogistica.pessoa2.fleet;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.sistemalogistica.pessoa2.contracts.v1.AssignmentRequest;
import br.com.sistemalogistica.pessoa2.contracts.v1.AssignmentSnapshot;
import br.com.sistemalogistica.pessoa2.contracts.v1.FleetType;
import br.com.sistemalogistica.pessoa2.contracts.v1.PartnerLookupPort;
import br.com.sistemalogistica.pessoa2.contracts.v1.ReleaseReason;
import br.com.sistemalogistica.pessoa2.contracts.v1.ReleaseReceipt;
import br.com.sistemalogistica.pessoa2.contracts.v1.ReleaseRequest;
import br.com.sistemalogistica.pessoa2.contracts.v1.RouteSnapshot;
import br.com.sistemalogistica.pessoa2.contracts.v1.RouteSnapshotPort;
import br.com.sistemalogistica.pessoa2.contracts.v1.RouteStatus;
import br.com.sistemalogistica.pessoa2.shared.contract.ContractException;

@Service
public class FleetAssignmentTransactionService {

	private final FleetAssignmentRepository assignmentRepository;
	private final FleetReleaseEventRepository releaseEventRepository;
	private final DriverRepository driverRepository;
	private final VehicleRepository vehicleRepository;
	private final RouteSnapshotPort routeSnapshotPort;
	private final PartnerLookupPort partnerLookupPort;

	public FleetAssignmentTransactionService(
			FleetAssignmentRepository assignmentRepository,
			FleetReleaseEventRepository releaseEventRepository,
			DriverRepository driverRepository,
			VehicleRepository vehicleRepository,
			RouteSnapshotPort routeSnapshotPort,
			PartnerLookupPort partnerLookupPort) {
		this.assignmentRepository = assignmentRepository;
		this.releaseEventRepository = releaseEventRepository;
		this.driverRepository = driverRepository;
		this.vehicleRepository = vehicleRepository;
		this.routeSnapshotPort = routeSnapshotPort;
		this.partnerLookupPort = partnerLookupPort;
	}

	@Transactional
	public AssignmentSnapshot reserve(AssignmentRequest request) {
		RouteSnapshot route = routeSnapshotPort.getById(request.routeId());
		if (route.status() != RouteStatus.PLANNED) {
			throw new ContractException("CONFLICT", "Only PLANNED routes can be assigned.");
		}
		if (!route.originId().equals(request.originId())) {
			throw new ContractException("PRECONDITION_FAILED", "The requested origin does not match the route.");
		}
		partnerLookupPort.getOrigin(request.originId());

		Selection selection = selectResources(request);
		FleetAssignment assignment = assignmentRepository.saveAndFlush(new FleetAssignment(
				UUID.randomUUID(),
				request,
				selection.driver().getId(),
				selection.vehicle().getId(),
				selection.vehicle().getCarrierId(),
				selection.vehicle().getFleetType()));
		return snapshot(assignment);
	}

	@Transactional
	public ReleaseReceipt release(ReleaseRequest request) {
		routeSnapshotPort.getById(request.routeId());
		if (request.reason() == ReleaseReason.ROUTE_CANCELLED) {
			throw new ContractException(
					"PRECONDITION_FAILED",
					"Fleet reservations cannot be released for route cancellation before that rule is approved.");
		}

		FleetReleaseEvent existingEvent = releaseEventRepository.findById(request.eventId()).orElse(null);
		if (existingEvent != null) {
			if (!existingEvent.getRouteId().equals(request.routeId()) || existingEvent.getReason() != request.reason()) {
				throw idempotencyConflict();
			}
			return new ReleaseReceipt(request.routeId(), false);
		}

		FleetAssignment assignment = assignmentRepository.findByRouteIdForUpdate(request.routeId())
				.orElseThrow(FleetAssignmentTransactionService::notFound);
		boolean released = assignment.getReleasedAt() == null;
		if (released) {
			assignment.release(Instant.now());
			assignmentRepository.saveAndFlush(assignment);
		}
		releaseEventRepository.saveAndFlush(
				new FleetReleaseEvent(request.eventId(), request.routeId(), request.reason()));
		return new ReleaseReceipt(request.routeId(), released);
	}

	private Selection selectResources(AssignmentRequest request) {
		List<FleetAssignment> activeAssignments = assignmentRepository.findAllByReleasedAtIsNull();
		Set<UUID> reservedDriverIds = new HashSet<>();
		Set<UUID> reservedVehicleIds = new HashSet<>();
		for (FleetAssignment assignment : activeAssignments) {
			reservedDriverIds.add(assignment.getDriverId());
			reservedVehicleIds.add(assignment.getVehicleId());
		}

		List<Driver> availableDrivers = driverRepository.findAllByActiveTrueOrderByIdAsc().stream()
				.filter(driver -> !reservedDriverIds.contains(driver.getId()))
				.toList();
		List<Vehicle> availableVehicles = vehicleRepository.findAllByActiveTrueOrderByIdAsc().stream()
				.filter(vehicle -> !reservedVehicleIds.contains(vehicle.getId()))
				.filter(vehicle -> vehicle.getCapacityKg().compareTo(request.requiredCapacityKg()) >= 0)
				.toList();

		Comparator<Selection> stableOrder = Comparator
				.comparingLong((Selection selection) ->
						assignmentRepository.countByDriverIdAndReleasedAtIsNull(selection.driver().getId()))
				.thenComparing(selection -> selection.driver().getId())
				.thenComparing(selection -> selection.vehicle().getId());

		List<Selection> ownFleet = new ArrayList<>();
		List<Selection> thirdPartyFleet = new ArrayList<>();
		for (Driver driver : availableDrivers) {
			for (Vehicle vehicle : availableVehicles) {
				if (vehicle.getFleetType() == FleetType.OWN
						&& driver.getCarrierId() == null) {
					ownFleet.add(new Selection(driver, vehicle));
				} else if (vehicle.getFleetType() == FleetType.THIRD_PARTY
						&& driver.getCarrierId() != null
						&& Objects.equals(driver.getCarrierId(), vehicle.getCarrierId())) {
					thirdPartyFleet.add(new Selection(driver, vehicle));
				}
			}
		}

		return !ownFleet.isEmpty()
				? ownFleet.stream().min(stableOrder).orElseThrow()
				: thirdPartyFleet.stream().min(stableOrder).orElseThrow(FleetAssignmentTransactionService::noEligible);
	}

	private static AssignmentSnapshot snapshot(FleetAssignment assignment) {
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
		return new ContractException("IDEMPOTENCY_CONFLICT", "The event ID was already used with a different payload.");
	}

	private static ContractException notFound() {
		return new ContractException("NOT_FOUND", "Fleet assignment was not found.");
	}

	private record Selection(Driver driver, Vehicle vehicle) {
	}
}
