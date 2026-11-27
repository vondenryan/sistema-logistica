package contracts.v1;

import java.math.*;
import java.time.*;
import java.util.*;

public record OriginSnapshot(
    UUID id, OriginType type, UUID storeId, AddressSnapshot address, boolean active) {}
