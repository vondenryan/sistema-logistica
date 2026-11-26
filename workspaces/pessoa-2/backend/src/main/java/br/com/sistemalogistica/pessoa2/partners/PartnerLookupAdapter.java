package br.com.sistemalogistica.pessoa2.partners;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import contracts.v1.AddressSnapshot;
import contracts.v1.OriginSnapshot;
import contracts.v1.OriginType;
import contracts.v1.PartnerLookupPort;
import contracts.v1.StoreSnapshot;
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
						BigDecimal.valueOf(address.latitude()),
						BigDecimal.valueOf(address.longitude())),
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
