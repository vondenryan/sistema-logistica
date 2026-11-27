package br.com.sistemalogistica.pessoa2.fleet;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.sistemalogistica.pessoa2.security.CurrentActorPort;
import br.com.sistemalogistica.pessoa2.shared.contract.ContractException;

@Service
public class CarrierService {

	private static final String NAME_INDEX = "ux_carriers_name_ci";

	private final CarrierRepository repository;
	private final CurrentActorPort currentActorPort;

	public CarrierService(CarrierRepository repository, CurrentActorPort currentActorPort) {
		this.repository = repository;
		this.currentActorPort = currentActorPort;
	}

	@Transactional(readOnly = true)
	public FleetPageResponse<CarrierResponse> list(int page, int size) {
		FleetSupport.requireAdministrativeActor(currentActorPort);
		Page<Carrier> carriers = repository.findAllByOrderByCreatedAtAscIdAsc(FleetSupport.pageRequest(page, size));
		return FleetSupport.page(carriers.map(CarrierResponse::from), page, size);
	}

	@Transactional(readOnly = true)
	public CarrierResponse get(UUID carrierId) {
		FleetSupport.requireAdministrativeActor(currentActorPort);
		return CarrierResponse.from(repository.findById(carrierId).orElseThrow(CarrierService::notFound));
	}

	@Transactional
	public CarrierResponse create(CarrierCreateRequest request) {
		FleetSupport.requireAdministrativeActor(currentActorPort);
		String name = FleetSupport.normalizeName(request.name());
		if (repository.existsByNameIgnoreCase(name)) {
			throw conflict();
		}
		return CarrierResponse.from(save(new Carrier(name, request.active())));
	}

	@Transactional
	public CarrierResponse patch(UUID carrierId, CarrierPatchRequest request) {
		FleetSupport.requireAdministrativeActor(currentActorPort);
		if (request.name() == null && request.active() == null) {
			throw ContractException.validation("body", "At least one field must be provided.");
		}

		Carrier carrier = repository.findById(carrierId).orElseThrow(CarrierService::notFound);
		String name = request.name() == null ? carrier.getName() : FleetSupport.normalizeName(request.name());
		boolean active = request.active() == null ? carrier.isActive() : request.active();
		if (!name.equalsIgnoreCase(carrier.getName())
				&& repository.existsByNameIgnoreCaseAndIdNot(name, carrierId)) {
			throw conflict();
		}

		carrier.update(name, active);
		return CarrierResponse.from(save(carrier));
	}

	private Carrier save(Carrier carrier) {
		try {
			return repository.saveAndFlush(carrier);
		} catch (DataIntegrityViolationException exception) {
			if (FleetSupport.isConstraintViolation(exception, NAME_INDEX)) {
				throw conflict();
			}
			throw exception;
		}
	}

	private static ContractException conflict() {
		return FleetSupport.conflict("name", "A carrier with this name already exists.");
	}

	private static ContractException notFound() {
		return new ContractException("NOT_FOUND", "Carrier was not found.");
	}
}
