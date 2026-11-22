package br.com.sistemalogistica.pessoa2.partners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.sistemalogistica.pessoa2.contracts.v1.AddressSnapshot;
import br.com.sistemalogistica.pessoa2.contracts.v1.OriginSnapshot;
import br.com.sistemalogistica.pessoa2.contracts.v1.OriginType;
import br.com.sistemalogistica.pessoa2.contracts.v1.StoreSnapshot;
import br.com.sistemalogistica.pessoa2.origins.Origin;
import br.com.sistemalogistica.pessoa2.origins.OriginRepository;
import br.com.sistemalogistica.pessoa2.shared.contract.ContractException;
import br.com.sistemalogistica.pessoa2.stores.Store;
import br.com.sistemalogistica.pessoa2.stores.StoreRepository;

class PartnerLookupAdapterTests {

	private final OriginRepository originRepository = mock(OriginRepository.class);
	private final StoreRepository storeRepository = mock(StoreRepository.class);
	private PartnerLookupAdapter adapter;

	@BeforeEach
	void setUp() {
		adapter = new PartnerLookupAdapter(originRepository, storeRepository);
	}

	@Test
	void mapsOriginToContractSnapshotWithoutChangingInactiveStatus() {
		UUID storeId = UUID.randomUUID();
		Origin origin = new Origin(
				br.com.sistemalogistica.pessoa2.origins.OriginType.DISTRIBUTION_CENTER,
				storeId,
				new br.com.sistemalogistica.pessoa2.origins.AddressSnapshot(
						"Rua Exemplo, 100", null, "Cidade Fictícia", "SC", "00000-000", "BR", -26.48, -49.07),
				false);
		when(originRepository.findById(origin.getId())).thenReturn(Optional.of(origin));

		OriginSnapshot snapshot = adapter.getOrigin(origin.getId());

		assertEquals(new OriginSnapshot(
				origin.getId(),
				OriginType.DISTRIBUTION_CENTER,
				storeId,
				new AddressSnapshot(
						"Rua Exemplo, 100", null, "Cidade Fictícia", "SC", "00000-000", "BR", -26.48, -49.07),
				false), snapshot);
		verify(originRepository).findById(origin.getId());
	}

	@Test
	void mapsStoreToContractSnapshot() {
		Store store = new Store("Loja inativa", false);
		when(storeRepository.findById(store.getId())).thenReturn(Optional.of(store));

		StoreSnapshot snapshot = adapter.getStore(store.getId());

		assertEquals(new StoreSnapshot(store.getId(), false, "Loja inativa"), snapshot);
		verify(storeRepository).findById(store.getId());
	}

	@Test
	void throwsNotFoundOnlyWhenOriginOrStoreIsAbsent() {
		UUID originId = UUID.randomUUID();
		UUID storeId = UUID.randomUUID();
		when(originRepository.findById(originId)).thenReturn(Optional.empty());
		when(storeRepository.findById(storeId)).thenReturn(Optional.empty());

		assertEquals("NOT_FOUND", assertThrows(ContractException.class, () -> adapter.getOrigin(originId)).getCode());
		assertEquals("NOT_FOUND", assertThrows(ContractException.class, () -> adapter.getStore(storeId)).getCode());
	}
}
