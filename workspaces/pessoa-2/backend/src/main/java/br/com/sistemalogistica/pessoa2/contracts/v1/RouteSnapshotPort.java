package br.com.sistemalogistica.pessoa2.contracts.v1;

import java.util.UUID;

public interface RouteSnapshotPort {

	RouteSnapshot getById(UUID routeId);
}
