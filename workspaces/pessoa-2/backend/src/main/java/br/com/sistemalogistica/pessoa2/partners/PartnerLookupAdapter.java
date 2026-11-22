package br.com.sistemalogistica.pessoa2.partners;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.sistemalogistica.pessoa2.contracts.v1.AddressSnapshot;
import br.com.sistemalogistica.pessoa2.contracts.v1.OriginSnapshot;
import br.com.sistemalogistica.pessoa2.contracts.v1.OriginType;
import br.com.sistemalogistica.pessoa2.contracts.v1.PartnerLookupPort;
import br.com.sistemalogistica.pessoa2.contracts.v1.StoreSnapshot;
import br.com.sistemalogistica.pessoa2.origins.Origin;
import br.com.sistemalogistica.pessoa2.origins.OriginRepository;
import br.com.sistemalogistica.pessoa2.shared.contract.ContractException;
import br.com.sistemalogistica.pessoa2.stores.Store;
import br.com.sistemalogistica.pessoa2.stores.StoreRepository;

@Component
public class PartnerLookupAdapter implements PartnerLookupPort {

	private final OriginRepository originRepository;
	private final StoreRepository storeRepository;

	public PartnerLookupAdapter(OriginRepository originRepository, StoreRepository storeRepository) {
		this.originRepository = originRepository;
		this.storeRepository = storeRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public OriginSnapshot getOrigin(UUID originId) {
		Origin origin = originRepository.findById(originId)
				.orElseThrow(() -> new ContractException("NOT_FOUND", "Origin was not found."));
		br.com.sistemalogistica.pessoa2.origins.AddressSnapshot address = origin.getAddress();

		return new OriginSnapshot(
				origin.getId(),
				toContractOriginType(origin.getType()),
				origin.getStoreId(),
				new AddressSnapshot(
						address.line1(),
						address.line2(),
						address.city(),
						address.state(),
						address.postalCode(),
						address.country(),
						address.latitude(),
						address.longitude()),
				origin.isActive());
	}

	@Override
	@Transactional(readOnly = true)
	public StoreSnapshot getStore(UUID storeId) {
		Store store = storeRepository.findById(storeId)
				.orElseThrow(() -> new ContractException("NOT_FOUND", "Store was not found."));
		return new StoreSnapshot(store.getId(), store.isActive(), store.getName());
	}

	private OriginType toContractOriginType(br.com.sistemalogistica.pessoa2.origins.OriginType type) {
		return switch (type) {
			case STORE -> OriginType.STORE;
			case DISTRIBUTION_CENTER -> OriginType.DISTRIBUTION_CENTER;
		};
	}
}
