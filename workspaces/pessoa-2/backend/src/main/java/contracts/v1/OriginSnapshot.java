package contracts.v1;

import java.util.UUID;

public record OriginSnapshot(
		UUID id,
		OriginType type,
		UUID storeId,
		AddressSnapshot address,
		boolean active) {
}
