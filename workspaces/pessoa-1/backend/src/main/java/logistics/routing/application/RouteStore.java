package logistics.routing.application;

import contracts.v1.*;
import java.util.*;

public interface RouteStore {
  void create(RouteSnapshot route);

  RouteSnapshot get(UUID id, boolean lock);

  void reorder(UUID id, List<UUID> ids);

  void assign(UUID id, AssignmentSnapshot assignment);

  void start(UUID id);

  void complete(UUID id);

  void done(UUID routeId, UUID orderId);

  boolean pending(UUID routeId);

  List<UUID> pendingRoutes();

  boolean hasActiveOrder(UUID id);

  List<UUID> activeRoutes();

  void requeue(UUID routeId);
}
