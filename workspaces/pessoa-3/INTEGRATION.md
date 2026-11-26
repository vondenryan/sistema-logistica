# Plano de Integração e Adaptadores — Pessoa 3

Este documento detalha as portas implementadas, consumidas e a estratégia de substituição de fakes para a integração na Sprint 4.

## 1. Portas Produzidas (`deliveries`, `notifications`, `documents`)

| Porta | Interface Java | Status na Sprint 1–3 | Substituição na Sprint 4 |
|---|---|---|---|
| `DeliveryCommandPort` | `contracts.v1.DeliveryCommandPort` | Implementação real em `DeliveryService` | Mantida e exposta para chamadas diretas por Pessoa 1 (`orders` e `routing`) |
| `DeliveryStatusPort` | `contracts.v1.DeliveryStatusPort` | Implementação real em `DeliveryService` | Mantida e exposta para consulta por Pessoa 1 |
| `NotificationRequestPort` | `contracts.v1.NotificationRequestPort` | Implementação real em `NotificationService` (outbox persistente + Mailpit/fake SMTP) | Mantida e exposta para eventos de pedido da Pessoa 1 (`ORDER_CONFIRMED`) |
| `DocumentRequestPort` | `contracts.v1.DocumentRequestPort` | Implementação real em `DocumentService` (PDF simulado com marca d'água acadêmica) | Mantida e conectada ao `OrderSnapshotPort` real |

### Operações Suportadas:
- `DeliveryCommandPort.ensureCreated(UUID orderId, UUID eventId)`: cria entrega em estado `PENDING` para pedido confirmado; operação idempotente por `orderId`.
- `DeliveryCommandPort.assignRoute(UUID orderId, UUID routeId, UUID eventId)`: associa entrega a uma rota válida (`ASSIGNED`); idempotente por rota.
- `DeliveryStatusPort.getByOrderId(UUID orderId)`: consulta snapshot da entrega.
- `NotificationRequestPort.enqueue(NotificationRequest request)`: persiste a notificação com chave única `(event_id, event_type)`. Chamadas equivalentes retornam o mesmo ID e payloads divergentes recebem `IDEMPOTENCY_CONFLICT`.
- Transições de entrega gravam a intenção no outbox na mesma transação que o evento. O processador consulta o pedido depois do commit e aplica novas tentativas com atraso exponencial se a consulta ou o SMTP falhar. Um pedido sem e-mail válido termina em `SKIPPED`.
- O processador mantém um bloqueio de linha até registrar o resultado do envio. Uma interrupção entre o aceite pelo servidor SMTP e o commit do outbox pode levar a um novo envio.
- `DocumentRequestPort.ensureGenerated(DocumentRequest request)`: gera NF-e/CT-e simulados (`UNIQUE(order_id, document_type)`), exigindo frete cotado (`PRECONDITION_FAILED` se ausente).

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
- `POST /api/v1/documents`: Geração idempotente de documento simulado (`NFE_SIMULATED` ou `CTE_SIMULATED`), retornando `201` se novo ou `200` se já existente.
- `GET /api/v1/documents/{documentId}`: Consulta metadados do documento simulado com validação de loja/ator.
- `GET /api/v1/documents/{documentId}/pdf`: Download do PDF (`application/pdf`) com aviso **SIMULAÇÃO ACADÊMICA — SEM VALIDADE FISCAL**.


## IP de rastreamento e capacidade do limitador

Por padrão nenhum proxy é confiável: o limitador usa o IP da conexão. Para um proxy controlado, configure `TRACKING_TRUSTED_PROXY_IPS` com seus IPs literais exatos separados por vírgula (sem sub-redes ou nomes DNS). Mantenha `server.forward-headers-strategy=none` para preservar o peer original. O proxy deve **sobrescrever**, nunca acrescentar, `X-Forwarded-For` com o IP da conexão que ele recebeu. Exemplo em Nginx: `proxy_set_header X-Forwarded-For $remote_addr;`. Não há proxy de produção incluído neste workspace.

O backend só aceita um único IP literal nesse cabeçalho quando o peer está na lista de confiança. Listas, cabeçalhos duplicados, portas e zonas IPv6 são ignorados em favor do peer. `Forwarded` e `X-Real-IP` não determinam a chave. IPv6 equivalente e IPv4 mapeado são normalizados. Se houver outros proxies ou NAT antes desse ponto, valide a identidade observada na implantação; configurar a lista de confiança não autentica o conteúdo de um cabeçalho que um proxy deixe de sobrescrever.

`TRACKING_RATE_LIMIT_MAX_CLIENTS` limita a quantidade de clientes em memória (padrão 10000); cada cliente guarda no máximo `tracking.rate-limit.max-requests` instantes (padrão 30). Clientes sem requisições aceitas dentro da janela de 60 segundos são removidos ao processar novas consultas. Ao atingir a capacidade, novos clientes recebem 429 até haver espaço; os limites dos clientes ativos não são apagados para abrir espaço. O controle é local à instância e não substitui um limitador compartilhado em implantação com várias réplicas.

A paginação de entregas começa em `page=0`, aceita `size` entre 1 e 100 e rejeita com `400 VALIDATION_ERROR` qualquer deslocamento `page * size` acima de `Integer.MAX_VALUE`. O painel permite avançar/voltar e atualizar a página atual.
