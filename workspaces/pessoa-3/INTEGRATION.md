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


## IP de rastreamento e capacidade do limitador

Por padrão nenhum proxy é confiável: o limitador usa o IP da conexão. Para um proxy controlado, configure `TRACKING_TRUSTED_PROXY_IPS` com seus IPs literais exatos separados por vírgula (sem sub-redes ou nomes DNS). Mantenha `server.forward-headers-strategy=none` para preservar o peer original. O proxy deve **sobrescrever**, nunca acrescentar, `X-Forwarded-For` com o IP da conexão que ele recebeu. Exemplo em Nginx: `proxy_set_header X-Forwarded-For $remote_addr;`. Não há proxy de produção incluído neste workspace.

O backend só aceita um único IP literal nesse cabeçalho quando o peer está na lista de confiança. Listas, cabeçalhos duplicados, portas e zonas IPv6 são ignorados em favor do peer. `Forwarded` e `X-Real-IP` não determinam a chave. IPv6 equivalente e IPv4 mapeado são normalizados. Se houver outros proxies ou NAT antes desse ponto, valide a identidade observada na implantação; configurar a lista de confiança não autentica o conteúdo de um cabeçalho que um proxy deixe de sobrescrever.

`TRACKING_RATE_LIMIT_MAX_CLIENTS` limita a quantidade de clientes em memória (padrão 10000); cada cliente guarda no máximo `tracking.rate-limit.max-requests` instantes (padrão 30). Clientes sem requisições aceitas dentro da janela de 60 segundos são removidos ao processar novas consultas. Ao atingir a capacidade, novos clientes recebem 429 até haver espaço; os limites dos clientes ativos não são apagados para abrir espaço. O controle é local à instância e não substitui um limitador compartilhado em implantação com várias réplicas.

A paginação de entregas começa em `page=0`, aceita `size` entre 1 e 100 e rejeita com `400 VALIDATION_ERROR` qualquer deslocamento `page * size` acima de `Integer.MAX_VALUE`. O painel permite avançar/voltar e atualizar a página atual.
