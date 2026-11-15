package contracts.v1;

import java.math.*;
import java.time.*;
import java.util.*;

public record ActorContext(UUID actorId, Role role, UUID storeId, UUID driverId) {}
