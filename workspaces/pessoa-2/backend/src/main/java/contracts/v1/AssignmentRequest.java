package contracts.v1;

import java.math.*;
import java.time.*;
import java.util.*;

public record AssignmentRequest(UUID routeId, UUID originId, BigDecimal requiredCapacityKg) {}
