package contracts.v1;

import java.math.*;
import java.time.*;
import java.util.*;

public record ReleaseRequest(UUID routeId, UUID eventId, ReleaseReason reason) {}
