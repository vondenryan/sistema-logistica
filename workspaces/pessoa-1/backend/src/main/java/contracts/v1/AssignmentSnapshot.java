package contracts.v1;

import java.math.*;
import java.time.*;
import java.util.*;

public record AssignmentSnapshot(
    UUID assignmentId,
    UUID routeId,
    UUID driverId,
    UUID vehicleId,
    UUID carrierId,
    FleetType fleetType) {}
