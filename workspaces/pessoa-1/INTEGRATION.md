# Integração das portas v1

Fonte normativa: [CONTRACTS_V1.md](docs/CONTRACTS_V1.md). Na Sprint 1 os consumidores executam isolados com fakes locais e fixtures. Na integração, o projeto pai compõe implementações reais por injeção de dependência dentro do monólito; não há HTTP entre módulos nem acesso a entidades, repositórios ou SQL de outro módulo. HTTP `/api/v1` atende frontend e testes de caixa-preta. Contratos compartilhados e migrações integradas exigem coordenação e revisão humana.

| Fake/porta consumida por P1 | Produtor real na integração | Operações/efeito |
|---|---|---|
| `PartnerLookupPort` | P2 partners | `getOrigin(originId)` e `getStore(storeId)`; origem ativa e vinculada à loja |
| `FleetAssignmentPort` | P2 fleet | `assign(request)` por rota, capacidade `requiredCapacityKg` |
| `FleetReleasePort` | P2 fleet | `release(request)` por evento e rota, na conclusão ou cancelamento permitido |
| `DeliveryCommandPort` | P3 deliveries | `ensureCreated(orderId,eventId)` e `assignRoute(orderId,routeId,eventId)` |
| `DeliveryStatusPort` | P3 deliveries | `getByOrderId(orderId)` |
| `NotificationRequestPort` | P3 notifications | `enqueue(request)`, deduplicado por evento |
| `DocumentRequestPort` | P3 documents | `ensureGenerated(orderId,documentType)` |

P1 fornece `CurrentActorPort` (identity), `OrderSnapshotPort` (orders, inclusive busca por tracking code) e `RouteSnapshotPort` (routing) para consumidores P2/P3. Substituir cada fake somente na composição integrada e testar contratos, autorização e erros; não importar implementação de outro módulo. A confirmação usa `orderId` como chave idempotente; efeitos externos exigem `eventId`, transações locais e outbox/reconciliação. Não se presume atomicidade distribuída. Consolidar Flyway por schema e sequência global sem renomear migrações já aplicadas; executar testes de integração e E2E com produtores reais posteriormente.

O DAT aprovado não está disponível neste workspace. Cancelamentos tardios sem regra explícita permanecem bloqueados; tarifa inicial e provedor de geocodificação continuam pendentes. Nenhuma dessas decisões é inferida pela integração.
