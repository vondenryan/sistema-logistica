package contracts.v1;

import java.util.UUID;

public interface PartnerLookupPort {

	OriginSnapshot getOrigin(UUID originId);

	StoreSnapshot getStore(UUID storeId);
}
