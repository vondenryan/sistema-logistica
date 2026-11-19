# Integração das portas v1

Fonte normativa: [CONTRACTS_V1.md](docs/CONTRACTS_V1.md), versão 1.0.0. Sprint 3 usa o módulo P1 isolado; a composição com P2/P3 e o teste entre provedores são trabalho pendente da Sprint 4. A troca será por injeção de portas Java no monólito, sem HTTP interno, importação de entidades ou SQL alheio. A presença de um contrato ou fake não prova compatibilidade com um provedor real.

| Porta | Provedor → consumidor | Situação P1 independente / substituição pendente | Dados e identidade relevantes |
|---|---|---|---|
| CurrentActor | P1 identity → P1/P2/P3 | P1 usa JWT real; P2/P3 devem conectar identity na composição | actorId, role, storeId?/driverId?; ausência 401, escopo 403 ou recurso oculto 404 |
| OrderSnapshot | P1 orders → P3 | serviço P1 local; ligação P3 pendente | orderId, storeId, originId, status, endereços, recipientName, recipientEmail?, trackingCode, totalWeightKg, freightAmount nullable, currency BRL; lookup ID/código |
| RouteSnapshot | P1 routing → P2/P3 | serviço P1 local; ligação P2/P3 pendente | routeId, storeId, originId, status, orderIds, driverId/vehicleId/startedAt nullable |
| PartnerLookup | P2 partners → P1 | `Fixtures` dev/test; trocar por P2 | getOrigin/getStore; origin id, type, storeId, address, active; store id, active, name |
| FleetAssignment | P2 fleet → P1 | `Fixtures` dev/test; trocar por P2 | routeId, originId, requiredCapacityKg; assignmentId, routeId, driverId, vehicleId, carrierId?, fleetType; repetição por routeId |
| FleetRelease | P2 fleet → P1 | `Fixtures` dev/test; trocar por P2 | routeId, reason, eventId; repetição do evento não deve liberar duas vezes |
| DeliveryCommand | P3 deliveries → P1 | `Fixtures` dev/test; trocar por P3 | ensureCreated(orderId,eventId), assignRoute(orderId,routeId,eventId); entrega única por pedido |
| DeliveryStatus | P3 deliveries → P1 | `Fixtures` dev/test; trocar por P3 | getByOrderId; deliveryId, orderId, routeId nullable, status, updatedAt, receiverName nullable |
| NotificationRequest | P3 notifications → P1 | `Fixtures` dev/test; trocar por P3 | eventId, eventType, orderId, recipient/to, occurredAt; receipt notificationId/accepted; chave eventId+eventType |
| DocumentRequest | P3 documents → P1 | `Fixtures` dev/test; trocar por P3 | orderId, documentType; receipt documentId, orderId, documentType, status; chave pedido/tipo; frete exigido |

Os DTOs e assinaturas exatos são os do contrato congelado, inclusive `AssignmentRequest.requiredCapacityKg` decimal positivo, dinheiro em string com duas casas no JSON, peso/distância com até três, UUID e UTC. Enums são fechados; campos extras em responses são ignoráveis, requests desconhecidos são rejeitados. `ContractException` transporta códigos; HTTP traduz para RFC 9457 com `type,title,status,detail,code,details` e `traceId?`. Destacam-se `VALIDATION_ERROR` 400, `UNAUTHENTICATED` 401, `FORBIDDEN` 403, `NOT_FOUND` 404, `CONFLICT`/`ALREADY_ASSIGNED`/`NO_ELIGIBLE_RESOURCE`/`IDEMPOTENCY_CONFLICT` 409, `PRECONDITION_FAILED` 422 e `DEPENDENCY_UNAVAILABLE` 503. Ver tabela normativa para demais erros e retry.

Confirmação usa orderId; comandos/notificações usam eventId; repetição idêntica não duplica efeitos e payload divergente deve produzir conflito quando previsto. Transações locais não garantem atomicidade distribuída: integração requer outbox/reconciliação e testes de falha e repetição com provedores reais. Fake de roteamento é determinístico e não é OSRM; tarifa 2.50 no verificador é apenas fixture de teste, não decisão de implantação.

Flyway local usa V1–V6 em schemas P1. Na integração, inventariar históricos e versões de todos os workspaces, definir sequência global sem renomear ou editar migrações já aplicadas, testar banco limpo e histórico, constraints e dados preservados. Quaisquer correções locais novas exigem versão inédita. A ordem global e conflitos de versões entre equipes ainda não foram validados.

Antes de afirmar integração: comparar contratos e fixtures, executar testes de consumidor/provedor contra implementações reais, trocar cada fake na composição, testar autorização cruzada, falhas e recuperação, migrações e E2E com P2/P3. Cancelamento tardio, tarifa operacional e geocodificação permanecem decisões pendentes. Consulte [operações Sprint 3](docs/SPRINT_3_OPERATIONS.md) para a verificação isolada.
