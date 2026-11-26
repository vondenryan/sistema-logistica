package contracts.v1;

import java.util.UUID;

public record StoreSnapshot(UUID id, boolean active, String name) {
}
