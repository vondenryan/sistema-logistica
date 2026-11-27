package br.com.sistemalogistica.pessoa2.fleet;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import contracts.v1.FleetType;
import br.com.sistemalogistica.pessoa2.security.CurrentActorPort;
import br.com.sistemalogistica.pessoa2.shared.contract.ContractException;

@Service
public class VehicleService {

	private static final String IDENTIFIER_INDEX = "ux_vehicles_identifier";

	private final VehicleRepository repository;
	private final CarrierRepository carrierRepository;
	private final CurrentActorPort currentActorPort;

	public VehicleService(
			VehicleRepository repository,
			CarrierRepository carrierRepository,
			CurrentActorPort currentActorPort) {
		this.repository = repository;
		this.carrierRepository = carrierRepository;
		this.currentActorPort = currentActorPort;
	}

	@Transactional(readOnly = true)
	public FleetPageResponse<VehicleResponse> list(int page, int size) {
		FleetSupport.requireAdministrativeActor(currentActorPort);
		Page<Vehicle> vehicles =
				repository.findAllByOrderByCreatedAtAscIdAsc(FleetSupport.pageRequest(page, size));
		return FleetSupport.page(vehicles.map(VehicleResponse::from), page, size);
	}

	@Transactional(readOnly = true)
	public VehicleResponse get(UUID vehicleId) {
		FleetSupport.requireAdministrativeActor(currentActorPort);
		return VehicleResponse.from(repository.findById(vehicleId).orElseThrow(VehicleService::notFound));
	}

	@Transactional
	public VehicleResponse create(VehicleCreateRequest request) {
		FleetSupport.requireAdministrativeActor(currentActorPort);
		String identifier = FleetSupport.normalizeIdentifier(request.identifier());
		UUID carrierId = validateCarrier(request.fleetType(), request.carrierId());
		if (repository.existsByIdentifier(identifier)) {
			throw conflict();
		}
		Vehicle vehicle = new Vehicle(
				identifier,
				request.capacityKg(),
				request.fleetType(),
				carrierId,
				request.active());
		return VehicleResponse.from(save(vehicle));
	}

	@Transactional
	public VehicleResponse patch(UUID vehicleId, VehiclePatchRequest request) {
		FleetSupport.requireAdministrativeActor(currentActorPort);
		if (request.identifier() == null && request.capacityKg() == null && request.fleetType() == null
				&& !request.carrierIdProvided() && request.active() == null) {
			throw ContractException.validation("body", "At least one field must be provided.");
		}

		Vehicle vehicle = repository.findById(vehicleId).orElseThrow(VehicleService::notFound);
		String identifier = request.identifier() == null
				? vehicle.getIdentifier()
				: FleetSupport.normalizeIdentifier(request.identifier());
		var capacityKg = request.capacityKg() == null ? vehicle.getCapacityKg() : request.capacityKg();
		FleetType fleetType = request.fleetType() == null ? vehicle.getFleetType() : request.fleetType();
		UUID carrierId = resolveCarrier(request, vehicle, fleetType);
		boolean active = request.active() == null ? vehicle.isActive() : request.active();

		if (!identifier.equals(vehicle.getIdentifier())
				&& repository.existsByIdentifierAndIdNot(identifier, vehicleId)) {
			throw conflict();
		}

		vehicle.update(identifier, capacityKg, fleetType, carrierId, active);
		return VehicleResponse.from(save(vehicle));
	}

	private UUID resolveCarrier(VehiclePatchRequest request, Vehicle vehicle, FleetType fleetType) {
		if (fleetType == FleetType.OWN) {
			if (request.carrierIdProvided()) {
				throw ContractException.validation("carrierId", "Must be omitted for OWN fleet.");
			}
			return null;
		}

		UUID carrierId = request.carrierIdProvided() ? request.carrierId() : vehicle.getCarrierId();
		if (carrierId == null) {
			throw ContractException.validation("carrierId", "Must be provided for THIRD_PARTY fleet.");
		}
		FleetSupport.requireCarrier(carrierRepository, carrierId);
		return carrierId;
	}

	private UUID validateCarrier(FleetType fleetType, UUID carrierId) {
		if (fleetType == FleetType.OWN) {
			if (carrierId != null) {
				throw ContractException.validation("carrierId", "Must be omitted for OWN fleet.");
			}
			return null;
		}
		if (carrierId == null) {
			throw ContractException.validation("carrierId", "Must be provided for THIRD_PARTY fleet.");
		}
		FleetSupport.requireCarrier(carrierRepository, carrierId);
		return carrierId;
	}

	private Vehicle save(Vehicle vehicle) {
		try {
			return repository.saveAndFlush(vehicle);
		} catch (DataIntegrityViolationException exception) {
			if (FleetSupport.isConstraintViolation(exception, IDENTIFIER_INDEX)) {
				throw conflict();
			}
			throw exception;
		}
	}

	private static ContractException conflict() {
		return FleetSupport.conflict("identifier", "A vehicle with this identifier already exists.");
	}

	private static ContractException notFound() {
		return new ContractException("NOT_FOUND", "Vehicle was not found.");
	}
}
