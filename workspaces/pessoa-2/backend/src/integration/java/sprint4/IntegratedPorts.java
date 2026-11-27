package sprint4;

import br.com.sistemalogistica.pessoa2.contracts.v1.RouteStatus;
import br.com.sistemalogistica.pessoa2.fleet.FleetAssignmentService;
import br.com.sistemalogistica.pessoa2.security.ActorContext;
import br.com.sistemalogistica.pessoa2.security.Role;
import contracts.v1.AssignmentRequest;
import contracts.v1.AssignmentSnapshot;
import contracts.v1.ContractException;
import contracts.v1.CurrentActorPort;
import contracts.v1.FleetAssignmentPort;
import contracts.v1.FleetReleasePort;
import contracts.v1.FleetType;
import contracts.v1.ReleaseReceipt;
import contracts.v1.ReleaseRequest;
import contracts.v1.RouteSnapshotPort;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;

final class IntegratedPorts implements FleetAssignmentPort, FleetReleasePort,
        br.com.sistemalogistica.pessoa2.security.CurrentActorPort,
        br.com.sistemalogistica.pessoa2.contracts.v1.RouteSnapshotPort {

    private final CurrentActorPort identity;
    private final RouteSnapshotPort routes;
    private final ObjectProvider<FleetAssignmentService> fleet;

    IntegratedPorts(CurrentActorPort identity, RouteSnapshotPort routes,
            ObjectProvider<FleetAssignmentService> fleet) {
        this.identity = identity;
        this.routes = routes;
        this.fleet = fleet;
    }

    @Override
    public ActorContext requireActor() {
        var actor = identity.requireActor();
        return new ActorContext(actor.actorId(), Role.valueOf(actor.role().name()), actor.storeId(), actor.driverId());
    }

    @Override
    public br.com.sistemalogistica.pessoa2.contracts.v1.RouteSnapshot getById(UUID id) {
        var route = routes.getById(id);
        return new br.com.sistemalogistica.pessoa2.contracts.v1.RouteSnapshot(
            route.routeId(), route.storeId(), route.originId(), RouteStatus.valueOf(route.status().name()),
            route.orderIds(), route.driverId(), route.vehicleId(), route.startedAt());
    }

    @Override
    public AssignmentSnapshot assign(AssignmentRequest request) {
        if (request == null) {
            throw new ContractException("VALIDATION_ERROR");
        }
        var result = call(() -> fleet.getObject().assign(
            new br.com.sistemalogistica.pessoa2.contracts.v1.AssignmentRequest(
                request.routeId(), request.originId(), request.requiredCapacityKg())));
        return new AssignmentSnapshot(result.assignmentId(), result.routeId(), result.driverId(),
            result.vehicleId(), result.carrierId(), FleetType.valueOf(result.fleetType().name()));
    }

    @Override
    public ReleaseReceipt release(ReleaseRequest request) {
        if (request == null || request.reason() == null) {
            throw new ContractException("VALIDATION_ERROR");
        }
        var result = call(() -> fleet.getObject().release(
            new br.com.sistemalogistica.pessoa2.contracts.v1.ReleaseRequest(
                request.routeId(), request.eventId(),
                br.com.sistemalogistica.pessoa2.contracts.v1.ReleaseReason.valueOf(request.reason().name()))));
        return new ReleaseReceipt(result.routeId(), result.released());
    }

    static <T> T call(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (br.com.sistemalogistica.pessoa2.shared.contract.ContractException e) {
            throw new ContractException(e.getCode(), e.getDetails());
        }
    }
}
