package br.com.sistemalogistica.pessoa2.fleet;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.sistemalogistica.pessoa2.security.CurrentActorPort;
import br.com.sistemalogistica.pessoa2.shared.contract.ContractException;

@Service
public class DriverService {

	private final DriverRepository repository;
	private final CarrierRepository carrierRepository;
	private final CurrentActorPort currentActorPort;

	public DriverService(
			DriverRepository repository,
			CarrierRepository carrierRepository,
			CurrentActorPort currentActorPort) {
		this.repository = repository;
		this.carrierRepository = carrierRepository;
		this.currentActorPort = currentActorPort;
	}

	@Transactional(readOnly = true)
	public FleetPageResponse<DriverResponse> list(int page, int size) {
		FleetSupport.requireAdministrativeActor(currentActorPort);
		Page<Driver> drivers = repository.findAllByOrderByCreatedAtAscIdAsc(FleetSupport.pageRequest(page, size));
		return FleetSupport.page(drivers.map(DriverResponse::from), page, size);
	}

	@Transactional(readOnly = true)
	public DriverResponse get(UUID driverId) {
		FleetSupport.requireAdministrativeActor(currentActorPort);
		return DriverResponse.from(repository.findById(driverId).orElseThrow(DriverService::notFound));
	}

	@Transactional
	public DriverResponse create(DriverCreateRequest request) {
		FleetSupport.requireAdministrativeActor(currentActorPort);
		String name = FleetSupport.normalizeName(request.name());
		if (request.carrierId() != null) {
			FleetSupport.requireCarrier(carrierRepository, request.carrierId());
		}
		return DriverResponse.from(
				repository.saveAndFlush(new Driver(name, request.carrierId(), request.active())));
	}

	@Transactional
	public DriverResponse patch(UUID driverId, DriverPatchRequest request) {
		FleetSupport.requireAdministrativeActor(currentActorPort);
		if (request.name() == null && !request.carrierIdProvided() && request.active() == null) {
			throw ContractException.validation("body", "At least one field must be provided.");
		}

		Driver driver = repository.findById(driverId).orElseThrow(DriverService::notFound);
		String name = request.name() == null ? driver.getName() : FleetSupport.normalizeName(request.name());
		UUID carrierId = request.carrierIdProvided() ? request.carrierId() : driver.getCarrierId();
		if (request.carrierIdProvided()) {
			FleetSupport.requireCarrier(carrierRepository, carrierId);
		}
		boolean active = request.active() == null ? driver.isActive() : request.active();

		driver.update(name, carrierId, active);
		return DriverResponse.from(repository.saveAndFlush(driver));
	}

	private static ContractException notFound() {
		return new ContractException("NOT_FOUND", "Driver was not found.");
	}
}
