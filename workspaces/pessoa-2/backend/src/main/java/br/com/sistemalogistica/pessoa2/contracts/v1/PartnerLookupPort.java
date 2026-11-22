package br.com.sistemalogistica.pessoa2.contracts.v1;

import java.util.UUID;

public interface PartnerLookupPort {

	OriginSnapshot getOrigin(UUID originId);

	StoreSnapshot getStore(UUID storeId);
}
