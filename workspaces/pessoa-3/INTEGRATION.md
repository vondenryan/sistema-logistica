# Plano de Integração e Adaptadores — Pessoa 3

Este documento detalha as portas implementadas, consumidas e a estratégia de substituição de fakes para a integração na Sprint 4.

## 1. Portas Produzidas (Módulo `deliveries`)

| Porta | Interface Java | Status na Sprint 1 | Substituição na Sprint 4 |
|---|---|---|---|
| `DeliveryCommandPort` | `contracts.v1.DeliveryCommandPort` | Implementação real em `DeliveryService` | Mantida e exposta para chamadas diretas por Pessoa 1 (`orders` e `routing`) |
| `DeliveryStatusPort` | `contracts.v1.DeliveryStatusPort` | Implementação real em `DeliveryService` | Mantida e exposta para consulta por Pessoa 1 |

### Operações Suportadas:
- `ensureCreated(UUID orderId, UUID eventId)`: cria entrega em estado `PENDING` para pedido confirmado; operação idempotente por `orderId`.
- `assignRoute(UUID orderId, UUID routeId, UUID eventId)`: associa entrega a uma rota válida (`ASSIGNED`); idempotente por rota.
- `getByOrderId(UUID orderId)`: consulta snapshot da entrega.

---

## 2. Portas Consumidas e Fakes Utilizados

| Porta | Provedor Real | Adaptador na Sprint 1–3 | Estratégia de Troca na Sprint 4 |
|---|---|---|---|
| `OrderSnapshotPort` | Pessoa 1 (`orders`) | `logistics.fakes.FakeOrderSnapshotPort` | Injetar adaptador real em memória ou cliente Java fornecido pelo módulo `orders` |
| `RouteSnapshotPort` | Pessoa 1 (`routing`) | `logistics.fakes.FakeRouteSnapshotPort` | Injetar adaptador real em memória fornecido pelo módulo `routing` |
| `CurrentActorPort` | Pessoa 1 (`identity`) | `logistics.fakes.FakeCurrentActorPort` | Injetar adaptador que lê o contexto de segurança real extraído do JWT |

---

## 3. Endpoints REST Expostos (`/api/v1`)

- `GET /api/v1/deliveries/my`: Entregas paginadas (`{items, page, size, totalItems}`) atribuídas ao motorista autenticado (`Role.DRIVER`).
- `PATCH /api/v1/deliveries/{id}/status`: Transições de status (`IN_TRANSIT`, `FAILED_ATTEMPT`, `ASSIGNED`).
- `POST /api/v1/deliveries/{id}/complete`: Conclusão da entrega (`DELIVERED`) com `receiverName` obrigatório.
- `GET /api/v1/deliveries/{id}`: Detalhes da entrega com validação de escopo do ator.
- `GET /api/v1/deliveries/{id}/events`: Histórico auditável e imutável de eventos da entrega.
- `GET /api/v1/tracking/{code}`: Rastreamento público sem JWT com rate limit (`429 RATE_LIMITED`), expondo apenas `status` e `milestones`.
