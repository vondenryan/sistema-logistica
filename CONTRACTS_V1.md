# CONTRACTS_V1 — Contratos de integração do Sistema de Gestão Logística

**Versão:** 1.0.0 · **Status:** especificação proposta para congelamento antes da Sprint 1 · **Escopo:** MVP acadêmico, 3 desenvolvedores, 7 dias.  
**Documentos auditados integralmente:** `SPRINTS_PESSOA_1_50.md`, `SPRINTS_PESSOA_2_25.md`, `SPRINTS_PESSOA_3_25.md` (versão 2.1 disponível).  
**Precedência:** decisões de negócio aprovadas no DAT v1.1 > este documento para contratos de integração > instruções de implementação das Sprints. Divergências exigem registro e aprovação; este documento **não** altera requisitos de negócio.

> **Regra para agentes:** não inferir campos, status ou rotas divergentes. Contratos `v1` são imutáveis em mudanças incompatíveis. Se um requisito depender de decisão de negócio pendente, implementar uma porta/feature flag desabilitada e registrar impedimento, sem inventar comportamento.

## 1. Auditoria cruzada: achados e resolução

| ID | Divergência/risco encontrado nos três planejamentos | Resolução normativa v1 | Impacto nos planejamentos |
|---|---|---|---|
| A01 | Erros definidos como `{code,message,details}` no início e como `ProblemDetail` mais adiante | HTTP usa **RFC 9457 Problem Details** com `type,title,status,detail,code,details,traceId?`; portas internas lançam `ContractException(code,details)` | Substituir definição curta inicial nas três Sprints |
| A02 | Endpoints sem `/api/v1` nas decisões específicas | Prefixo **obrigatório** `/api/v1` para todas as rotas de negócio, inclusive auth e tracking | Corrigir exemplos nas Sprints 1 e 3 |
| A03 | `FleetAssignmentPort.requiredCapacity` vs `requiredCapacityKg` | `requiredCapacityKg: decimal(12,3)`, positivo; sem campo legado | Corrigir tabela inicial nas três Sprints |
| A04 | `PartnerLookupPort` aceita `storeId` **ou** `originId` em uma tabela, mas apenas `originId` em outra | `getOrigin(originId)` e `getStore(storeId)` como operações separadas | Corrigir tabela inicial nas três Sprints |
| A05 | `RouteSnapshotPort` omite origem, estado e `storeId` necessários a autorização/validação | Snapshot inclui `originId`, `status`, `storeId`, `orderIds`, `driverId?`, `vehicleId?`, `startedAt?` | Atualizar DTO das três Sprints |
| A06 | `OrderSnapshotPort` omite estado de confirmação, peso e moeda; `freightAmount` sem nullabilidade | Definir `status`, `totalWeightKg`, `freightAmount?`, `currency=BRL` e snapshots de endereço | Atualizar DTO das três Sprints |
| A07 | Pessoa 1 confirma pedido e exige criação de entrega, mas falta porta de comando | Adicionar `DeliveryCommandPort.ensureCreated` idempotente (Pessoa 3) | Acrescentar à Pessoa 1 e Pessoa 3 |
| A08 | Pessoa 2 precisa liberar reservas por conclusão/cancelamento, sem evento formal | Adicionar `FleetReleasePort.release(routeId,reason,eventId)` (Pessoa 2) e evento de conclusão de rota emitido pela Pessoa 1 | Acrescentar à Pessoa 1 e Pessoa 2 |
| A09 | Pessoa 3 precisa consultar tracking por código sem SQL de pedidos | Adicionar `OrderSnapshotPort.findByTrackingCode(code)` com retorno interno protegido; endpoint público sanitiza saída | Acrescentar à Pessoa 1 e Pessoa 3 |
| A10 | Risco de dependência circular `routing ↔ fleet ↔ deliveries` | **Dependência de compilação unidirecional** para `contracts/v1` apenas; portas injetadas, sem imports de módulos; comandos/eventos coordenados por aplicação | Esclarecer nos três arquivos |
| A11 | Confirmação idempotente, entrega única e pagamento fictício único sem chave/correlação | `orderId` como chave natural idempotente de confirmação; `eventId` para eventos; constraints únicas locais | Esclarecer Pessoas 1 e 3 |
| A12 | Origem pode ser loja ou centro de distribuição, mas vínculo da origem com loja não estava fixo | `origin.storeId` obrigatório para ambas; autorizar por loja vinculada | Esclarecer Pessoas 1 e 2 |
| A13 | Documentos sem formato do request e sem regra de duplicidade | `DocumentRequestPort.ensureGenerated(orderId,documentType)` idempotente por par | Esclarecer Pessoas 1 e 3 |
| A14 | Notificação de confirmação pode ser solicitada por Pessoa 1; notificação de entrega por Pessoa 3 | `NotificationRequestPort` implementada exclusivamente por Pessoa 3; todos consumidores enviam eventos idempotentes | Esclarecer Pessoas 1 e 3 |
| A15 | Migrações locais independentes podem colidir ao integrar | Schemas por módulo, arquivos Flyway versionados por **prefixo e sequência global atribuídos** na integração; sem renomear migrações já aplicadas | Esclarecer os três |
| A16 | Ambiguidade entre banco local por workspace e PostgreSQL Supabase final | Cada workspace usa PostgreSQL local isolado; integração usa **uma instância lógica PostgreSQL** (Supabase previsto), schemas por módulo | Esclarecer os três |
| A17 | Sobreposição: Pessoa 1 infraestrutura Vue/JWT; Pessoas 2/3 usam autenticação fake e layout local | Somente Pessoa 1 implementa shell/identity reais; outras implementam componentes/telas autocontidos e `CurrentActorPort` fake dev | Esclarecer os três |
| A18 | Incompatibilidade potencial de `FAILED_ATTEMPT` como estado e reatribuição | Transições documentadas sem inventar nova etapa; regras de cancelamento tardio permanecem pendentes se DAT não definir | Esclarecer Pessoa 3 |
| A19 | Documento PDF e notificações podem depender de frete ainda não cotado | `freightAmount=null` até cotação; geração que requer frete falha com `PRECONDITION_FAILED` | Esclarecer Pessoa 3 |
| A20 | Portas não distinguem ausência, indisponibilidade e conflito | Catálogo único de erros e semântica de retry | Esclarecer todos |

### Pendências de negócio — NÃO decidir pelo agente

1. **Valor inicial da tarifa global** em BRL/km: pendente; configuração obrigatória na implantação, não atribuir valor arbitrário.
2. **Provedor de geocodificação**: pendente; `GeocodingProvider` desacoplado; entrada manual de coordenadas/fixtures deve funcionar.
3. **Versão exata do Spring Boot**: pendente de validação técnica e fixação única no build; Java 21 confirmado.
4. **Critério exato de cancelamento** depois de atribuição/início e eventual cancelamento de rota: seguir DAT; se não estiver explícito, bloquear a transição não especificada e registrar decisão pendente.
5. **Regra operacional para múltiplas origens em uma rota**: MVP não mistura origens incompatíveis; coletas múltiplas não definidas ficam fora do escopo.
6. **Política de correção de peso/dados de pedido após confirmação**: não alterar snapshots confirmados; fluxos de retificação não definidos ficam fora do MVP.

## 2. Arquitetura, propriedade e direção de dependências

| Proprietário | Módulos de produção | Produz | Consome |
|---|---|---|---|
| Pessoa 1 · Codex · 50% | `identity`, `orders`, `billing`, `routing`, shell frontend, Docker/CI | `CurrentActorPort`, `OrderSnapshotPort`, `RouteSnapshotPort`, eventos de pedido/rota | `PartnerLookupPort`, `FleetAssignmentPort`, `FleetReleasePort`, `DeliveryCommandPort`, `DeliveryStatusPort`, `NotificationRequestPort`, `DocumentRequestPort` |
| Pessoa 2 · Antigravity · 25% | `partners`, `fleet`, telas respectivas | `PartnerLookupPort`, `FleetAssignmentPort`, `FleetReleasePort` | `CurrentActorPort`, `RouteSnapshotPort` para validação por DTO |
| Pessoa 3 · Antigravity · 25% | `deliveries`, `notifications`, `documents`, telas respectivas | `DeliveryCommandPort`, `DeliveryStatusPort`, `NotificationRequestPort`, `DocumentRequestPort` | `OrderSnapshotPort`, `RouteSnapshotPort`, `CurrentActorPort` |

**Separação obrigatória:** `domain` puro → `application` (casos de uso + portas) → `infrastructure` (REST/JPA/SMTP/OSRM/PDF). Um módulo **não** importa classes `domain`, `application`, `infrastructure`, repositórios ou entidades de outro. Todos podem depender de **`contracts/v1`** (DTOs imutáveis, enums, erros, interfaces). Em monólito integrado, adaptadores locais implementam portas diretamente em memória/chamadas Java, **sem HTTP entre módulos**. REST é a interface frontend/backend e de testes de caixa-preta; não criar microserviços.

**Ciclo operacional sem dependência circular de compilação:** P1 confirma pedido → `DeliveryCommandPort.ensureCreated` P3 → `NotificationRequestPort` P3; P1 cria/atribui rota → `FleetAssignmentPort` P2 → `DeliveryCommandPort.assignRoute` P3; P3 atualiza entrega → `DeliveryStatusPort` P3; P1 conclui rota quando todas terminais → `FleetReleasePort` P2. Cada módulo depende somente dos contratos, e não da implementação do outro. Falhas intermediárias: transações locais + outbox/reconciliação idempotente; **não** prometer atomicidade distribuída entre bancos/workspaces.

**Política de contratos:** `contracts/v1/` é somente leitura para agentes sem aprovação humana. Alterações aditivas opcionais podem ser propostas com testes de compatibilidade; remoção, renomeação, alteração de tipo/semântica, enum novo não tolerado ou mudança de obrigatoriedade requerem `v2` e plano de migração. Usar SemVer no arquivo de contrato (`1.0.0`) e `/api/v1` na API. Não introduzir campo `v2` dentro de payload v1. Campo desconhecido: consumidores devem ignorar apenas campos **adicionais** de resposta; provedores rejeitam campos desconhecidos de requests (`400 VALIDATION_ERROR`) para detectar erros de agente. Enums desconhecidos em respostas são incompatíveis e devem falhar nos testes.

## 3. Tipos canônicos e convenções

| Tipo | Serialização JSON | Java | Validação |
|---|---|---|---|
| `UUID` | string minúscula UUID canônico | `java.util.UUID` | formato RFC 4122; não usar `orderId` como tracking code |
| `Instant` | string UTC `YYYY-MM-DDTHH:mm:ss.SSSZ` | `Instant` | entrada aceita offset, normaliza UTC na saída |
| `Money` | **string** decimal, ex. `"123.45"` | `BigDecimal` | escala 2, não negativa, BRL; nunca número float |
| `WeightKg` | **string** decimal, ex. `"40.000"` | `BigDecimal` | escala ≤3, >0 quando capacidade requerida |
| `DistanceKm` | **string** decimal, ex. `"12.345"` | `BigDecimal` | escala ≤3, ≥0; não confundir rota com frete |
| `Latitude/Longitude` | número JSON | `BigDecimal` ou `double` apenas geográfico | latitude [-90,90], longitude [-180,180] |
| `Email` | string | `String` | formato sintático válido, máximo 254 caracteres |
| `Text` | string UTF-8 | `String` | sem espaços em branco apenas; comprimento específico por campo |
| `Boolean` | boolean | `boolean` | `true/false` |

**Nullabilidade:** campo com `?` é opcional e **omitido quando ausente**, salvo campos explicitamente definidos como `nullable` que são enviados com `null`. Arrays obrigatórios nunca são `null`. IDs e datas são opacos para clientes. JSON `camelCase`; tabelas/colunas SQL `snake_case`. Content-Type `application/json; charset=utf-8` para requests JSON e responses JSON; `application/problem+json` para erros; PDF `application/pdf`. Header `Authorization: Bearer <jwt>` em endpoints protegidos. `X-Request-Id` UUID opcional, gerado pelo servidor se ausente e retornado na resposta. Datas de criação geradas pelo servidor, não pelo cliente. Páginas/listas usam `page` (0-based), `size` (1..100), resposta `{items,page,size,totalItems}`; ordenação estável por `createdAt,id` quando aplicável.

**Enums congelados:**
- `Role`: `ADMIN`, `OPERATOR`, `STORE`, `DRIVER`.
- `OriginType`: `STORE`, `DISTRIBUTION_CENTER`.
- `FleetType`: `OWN`, `THIRD_PARTY`.
- `DeliveryStatus`: `PENDING`, `ASSIGNED`, `IN_TRANSIT`, `FAILED_ATTEMPT`, `DELIVERED`, `CANCELLED`.
- `OrderStatus` (representação técnica mínima): `DRAFT`, `CONFIRMED`, `CANCELLED`; outros estados internos não podem aparecer neste contrato sem revisão.
- `RouteStatus` (representação técnica mínima): `PLANNED`, `ASSIGNED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`; cancelamento depende das regras pendentes.
- `DocumentType`: `NFE_SIMULATED`, `CTE_SIMULATED`.
- `DocumentStatus`: `GENERATED` (v1 gera documento de forma síncrona); erro de geração é resposta de erro, não estado silencioso.
- `NotificationEventType`: `ORDER_CONFIRMED`, `DELIVERY_IN_TRANSIT`, `DELIVERY_DELIVERED`, `DELIVERY_FAILED_ATTEMPT`.
- `ReleaseReason`: `ROUTE_COMPLETED`, `ROUTE_CANCELLED` (segundo valor só após regra de cancelamento aprovada).

### Erros HTTP — formato único

```json
{
  "type": "https://example.invalid/problems/validation-error",
  "title": "Validation error",
  "status": 400,
  "detail": "requiredCapacityKg must be greater than zero",
  "code": "VALIDATION_ERROR",
  "details": [{"field": "requiredCapacityKg", "reason": "MUST_BE_POSITIVE"}],
  "traceId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
}
```

`type` é URI identificadora estável (`https://example.invalid/problems/{kebab-case-code}` no MVP; não precisa ser navegável). `details` é sempre array (vazio quando sem erro de campo); `traceId` pode ser omitido se indisponível. Nunca expor stack trace, SQL, e-mail de terceiros ou tokens. Portas Java lançam `ContractException` com `code`, `details` e causa interna; adaptador REST traduz.

| HTTP | Código estável | Significado/retry |
|---|---|---|
| 400 | `VALIDATION_ERROR` | payload inválido; não repetir sem corrigir |
| 401 | `UNAUTHENTICATED` | JWT ausente/inválido |
| 403 | `FORBIDDEN` | ator sem papel/escopo; não repetir |
| 404 | `NOT_FOUND` | recurso ausente **ou oculto por escopo**, sem enumeração |
| 409 | `CONFLICT` | estado/transição conflitante |
| 409 | `ALREADY_ASSIGNED` | rota/recurso em conflito |
| 409 | `NO_ELIGIBLE_RESOURCE` | nenhum recurso de frota elegível |
| 409 | `IDEMPOTENCY_CONFLICT` | mesma chave, payload diferente |
| 422 | `PRECONDITION_FAILED` | falta condição de domínio, ex. frete não cotado |
| 429 | `RATE_LIMITED` | rastreamento público; `Retry-After` |
| 502 | `UPSTREAM_ERROR` | provedor externo respondeu incorretamente |
| 503 | `DEPENDENCY_UNAVAILABLE` | serviço necessário indisponível; retry com backoff |
| 500 | `INTERNAL_ERROR` | erro não previsto, sem detalhes internos |

## 4. Contratos de dados (schemas lógicos canônicos)

Todos os objetos abaixo são **fechados em request** e **extensíveis apenas com campos opcionais em response**. Os exemplos são fixtures e usam dados fictícios.

### 4.1 `AddressSnapshot`

```json
{"line1":"Rua Exemplo, 100","line2":null,"city":"Cidade Fictícia","state":"SC","postalCode":"00000-000","country":"BR","latitude":-26.480000,"longitude":-49.070000}
```

Obrigatórios `line1` (1..200), `city` (1..100), `state` (UF 2 letras), `country` (`BR` no MVP), `latitude`, `longitude`; `postalCode` opcional (se presente, string 8 dígitos ou CEP com hífen); `line2` **nullable** string até 100. Coordenadas são obrigatórias **para confirmar pedido e calcular rota**; durante rascunho podem estar ausentes no DTO de edição, não no snapshot confirmado. `address` é snapshot imutável, não referência a entidade JPA.

### 4.2 `OriginSnapshot`

```json
{"id":"11111111-1111-4111-8111-111111111111","type":"STORE","storeId":"22222222-2222-4222-8222-222222222222","address":{"line1":"Rua Exemplo, 100","line2":null,"city":"Cidade Fictícia","state":"SC","postalCode":"00000-000","country":"BR","latitude":-26.480000,"longitude":-49.070000},"active":true}
```

Todos os campos obrigatórios; `storeId` é a loja contratante à qual a origem está vinculada, **inclusive** se `type=DISTRIBUTION_CENTER`. `id` identifica a origem, não necessariamente a loja. P2 mantém o cadastro; P1 salva snapshot ao confirmar pedido.

### 4.3 `OrderSnapshot`

```json
{"orderId":"44444444-4444-4444-8444-444444444444","storeId":"22222222-2222-4222-8222-222222222222","originId":"11111111-1111-4111-8111-111111111111","status":"CONFIRMED","origin":{"line1":"Rua Exemplo, 100","line2":null,"city":"Cidade Fictícia","state":"SC","postalCode":"00000-000","country":"BR","latitude":-26.480000,"longitude":-49.070000},"destination":{"line1":"Rua Destino, 200","line2":null,"city":"Cidade Fictícia","state":"SC","postalCode":"00000-000","country":"BR","latitude":-26.490000,"longitude":-49.080000},"recipientName":"Cliente Fictício","recipientEmail":"cliente@example.invalid","trackingCode":"TRK-7M4Q9X2K8P","totalWeightKg":"40.000","freightAmount":"30.00","currency":"BRL"}
```

Obrigatórios todos, exceto `recipientEmail?` (omitido se não informado) e `freightAmount` (**nullable** até cotação: `null`). `trackingCode` é token aleatório com entropia adequada (mínimo 96 bits antes da codificação; não derivar de UUID sequencial ou nome), único e case-sensitive; fixture é apenas ilustrativa, **não** prova entropia. `status=CONFIRMED` é pré-condição para criar entrega; `totalWeightKg` soma pesos positivos de itens. `currency` sempre `BRL`.

### 4.4 `RouteSnapshot`

```json
{"routeId":"33333333-3333-4333-8333-333333333333","storeId":"22222222-2222-4222-8222-222222222222","originId":"11111111-1111-4111-8111-111111111111","status":"ASSIGNED","orderIds":["44444444-4444-4444-8444-444444444444"],"driverId":"55555555-5555-4555-8555-555555555555","vehicleId":"66666666-6666-4666-8666-666666666666","startedAt":null}
```

`routeId,storeId,originId,status,orderIds` obrigatórios; `orderIds` array de 1..10 UUIDs únicos, na ordem planejada. `driverId`, `vehicleId`, `startedAt` são **nullable** até os respectivos estados. `startedAt` só não-nulo em `IN_PROGRESS` ou `COMPLETED`. Uma rota tem origem única e um motorista/veículo quando atribuída.

### 4.5 `DeliverySnapshot`

```json
{"deliveryId":"77777777-7777-4777-8777-777777777777","orderId":"44444444-4444-4444-8444-444444444444","routeId":"33333333-3333-4333-8333-333333333333","status":"ASSIGNED","updatedAt":"2026-11-14T12:00:00.000Z","receiverName":null}
```

`deliveryId,orderId,status,updatedAt` obrigatórios; `routeId` e `receiverName` **nullable**. `receiverName` obrigatório e não vazio quando `status=DELIVERED`, não retornado no rastreamento público.

## 5. Portas Java v1: assinatura e semântica

As interfaces são especificação de **assinaturas lógicas**; implementá-las no pacote compartilhado `contracts.v1` com records imutáveis Java 21 e `BigDecimal`/`UUID`/`Instant`. Nenhum tipo de Spring/JPA nas assinaturas. `Optional` somente em métodos de lookup que podem não encontrar; falhas operacionais usam `ContractException`. Não retornar `null` em métodos Java. Os exemplos abaixo usam pseudocódigo Java compilável após importar os records de `contracts.v1`.

```java
interface PartnerLookupPort {
  OriginSnapshot getOrigin(UUID originId); // NOT_FOUND, nunca null
  StoreSnapshot getStore(UUID storeId);     // {id,active,name}; NOT_FOUND
}
interface FleetAssignmentPort {
  AssignmentSnapshot assign(AssignmentRequest request);
}
// AssignmentRequest(UUID routeId, UUID originId, BigDecimal requiredCapacityKg)
// AssignmentSnapshot(UUID assignmentId, UUID routeId, UUID driverId,
//   UUID vehicleId, UUID carrierId /* nullable */, FleetType fleetType)
interface FleetReleasePort {
  ReleaseReceipt release(ReleaseRequest request);
}
// ReleaseRequest(UUID routeId, UUID eventId, ReleaseReason reason)
// ReleaseReceipt(UUID routeId, boolean released) // false se já liberado
interface OrderSnapshotPort {
  OrderSnapshot getById(UUID orderId);
  OrderSnapshot findByTrackingCode(String trackingCode); // NOT_FOUND
}
interface RouteSnapshotPort {
  RouteSnapshot getById(UUID routeId);
}
interface DeliveryCommandPort {
  DeliverySnapshot ensureCreated(UUID orderId, UUID eventId);
  DeliverySnapshot assignRoute(UUID orderId, UUID routeId, UUID eventId);
}
interface DeliveryStatusPort {
  DeliverySnapshot getByOrderId(UUID orderId);
}
interface NotificationRequestPort {
  NotificationReceipt enqueue(NotificationRequest request);
}
// NotificationRequest(UUID eventId, NotificationEventType eventType,
//   UUID orderId, String to, Instant occurredAt)
// NotificationReceipt(UUID notificationId, boolean accepted)
interface DocumentRequestPort {
  DocumentReceipt ensureGenerated(DocumentRequest request);
}
// DocumentRequest(UUID orderId, DocumentType documentType)
// DocumentReceipt(UUID documentId, UUID orderId, DocumentType documentType,
//   DocumentStatus status)
interface CurrentActorPort {
  ActorContext requireActor();
}
// ActorContext(UUID actorId, Role role, UUID storeId /* nullable */,
//   UUID driverId /* nullable */)
```

**Regras de portas (normativas):**

| Porta/operação | Responsável real | Pré-condição e sucesso | Falha, duplicidade e autorização |
|---|---|---|---|
| `PartnerLookupPort.getOrigin/getStore` | P2 | Origem/loja ativa e vínculo `storeId` validável; snapshot sem JPA | `NOT_FOUND` se ausente; origem inativa retornada com `active=false` e P1 rejeita confirmação; acesso externo controlado pelo caso de uso chamador |
| `FleetAssignmentPort.assign` | P2 | Rota `PLANNED`, capacidade kg positiva, origem existente; escolher frota própria elegível, desempate determinístico, reservar motorista+veículo atomicamente | Mesmo `routeId` e mesma requisição → mesmo `assignmentId`; payload diferente → `IDEMPOTENCY_CONFLICT`; indisponível → `NO_ELIGIBLE_RESOURCE`; concorrência sem dupla reserva |
| `FleetReleasePort.release` | P2 | Libera reserva de rota concluída; `eventId` deduplica | Repetição retorna `released=false`; rota desconhecida → `NOT_FOUND`; `ROUTE_CANCELLED` depende de regra aprovada |
| `OrderSnapshotPort.getById/findByTrackingCode` | P1 | Retorna snapshot confirmado ou rascunho conforme método; tracking code é consulta **interna**, nunca expor resposta bruta ao público | Ausente → `NOT_FOUND`; P3 sanitiza saída; lookup por código usa comparação exata |
| `RouteSnapshotPort.getById` | P1 | Retorna snapshot de rota | Ausente → `NOT_FOUND`; sem inferir atribuição ausente |
| `DeliveryCommandPort.ensureCreated` | P3 | Pedido `CONFIRMED`; cria exatamente 1 entrega `PENDING` por `orderId` | Mesma `orderId`/evento → mesma entrega; `orderId` existente com evento novo não duplica; pedido não confirmado → `PRECONDITION_FAILED` |
| `DeliveryCommandPort.assignRoute` | P3 | Entrega `PENDING` ou `FAILED_ATTEMPT` elegível, rota atribuída contém `orderId` | Mesma rota/evento → mesmo resultado; outra rota ativa → `CONFLICT`; transição inválida → `CONFLICT` |
| `DeliveryStatusPort.getByOrderId` | P3 | Snapshot com status e timestamp | Ausente → `NOT_FOUND`; consulta não altera estado |
| `NotificationRequestPort.enqueue` | P3 | Enfileira e persiste outbox, `accepted=true` significa aceito **não enviado** | Mesmo `eventId+eventType` e payload idêntico → mesmo `notificationId`; payload diferente → `IDEMPOTENCY_CONFLICT`; falha persistência → `DEPENDENCY_UNAVAILABLE` |
| `DocumentRequestPort.ensureGenerated` | P3 | Documento acadêmico gerado com watermark, por `orderId+documentType` | Mesmo par → mesmo `documentId`; pedido ausente → `NOT_FOUND`; frete requerido ausente → `PRECONDITION_FAILED`; falha PDF → `INTERNAL_ERROR` |
| `CurrentActorPort.requireActor` | P1 | Contexto do JWT validado; `storeId` só STORE, `driverId` só DRIVER; ADMIN/OPERATOR podem ter ambos nulos | Sem identidade → `UNAUTHENTICATED`; fake só perfil `dev`/test, nunca produção |

**Nota sobre `FleetAssignmentPort`:** o método usa `RouteSnapshotPort` para validar que a rota existe e sua origem/carga corresponde ao request. P2 não importa módulo `routing`; injeção de adaptador fake/real. O campo `requiredCapacityKg` é calculado pela Pessoa 1 a partir dos pedidos, não pelo provedor de frota.

**Nota sobre notificações:** confirmação de pedido é emitida por P1; transições de entrega por P3. P3 implementa um único outbox. Em execução integrada, a emissão deve ser atômica com o evento de domínio **quando ambos estiverem na mesma transação**, ou reconciliável por eventId/outbox quando não estiverem. Não perder eventos silenciosamente.

**Nota sobre `DeliveryCommandPort.assignRoute`:** a integração P1 deve chamar após atribuição bem-sucedida. Se falhar, a rota fica pendente de reconciliação; não marcar entrega atribuída parcialmente como concluída. P1 registra `eventId` estável da operação para retry.

## 6. Contratos REST v1 (frontend ↔ backend)

Todos os endpoints de negócio são REST sobre `/api/v1`. Os DTOs REST são adaptadores para os contratos internos; não expor entidades JPA. **Regra geral de autorização:** ADMIN/OPERATOR operam o fluxo administrativo; STORE somente dados de sua loja; DRIVER somente suas entregas; consulta pública apenas `/tracking/{code}`. Em endpoints com IDs de outra loja, retornar 404 sem revelar existência. P2/P3 podem testar controllers com `CurrentActorPort` fake em perfil dev, não precisam do servidor P1.

### 6.1 Identity — P1

| Método e endpoint | Request | Response | HTTP | Autorização |
|---|---|---|---|---|
| `POST /api/v1/auth/login` | `{email:string,password:string}` obrigatórios | `{accessToken:string,tokenType:"Bearer",expiresAt:Instant,actor:ActorContext}` | 200; 400,401,429 | público; rate limit |
| `GET /api/v1/auth/me` | sem body | `ActorContext` | 200;401 | JWT |

`email` até 254, `password` não vazio (não registrar). JWT assinado com expiração, sem refresh token no MVP. `ActorContext` inclui `storeId:null`/`driverId:null` explicitamente quando ausentes. Login inválido retorna 401 genérico, sem distinguir usuário inexistente.

### 6.2 Partners — P2

| Método e endpoint | Request | Response | HTTP | Autorização |
|---|---|---|---|---|
| `GET /api/v1/origins/{originId}` | path UUID | `OriginSnapshot` | 200,400,401,403,404 | ADMIN/OPERATOR; STORE apenas origem da própria loja |
| `GET /api/v1/stores/{storeId}` | path UUID | `{id:UUID,name:string,active:boolean}` | 200,401,404 | ADMIN/OPERATOR; STORE próprio |
| `POST /api/v1/stores` | `{name:string,active:boolean}` | `{id:UUID,name:string,active:boolean}` | 201,400,401,403,409 | ADMIN/OPERATOR |
| `POST /api/v1/origins` | `{type:OriginType,storeId:UUID,address:AddressSnapshot,active:boolean}` | `OriginSnapshot` | 201,400,401,403,404 | ADMIN/OPERATOR |
| `POST /api/v1/suppliers` | `{name:string,contactEmail?:string,storeId:UUID}` | `{id:UUID,name:string,contactEmail?:string,storeId:UUID}` | 201,400,401,403,404 | ADMIN/OPERATOR |

CRUD de atualização/listagem/remoção lógica segue os mesmos DTOs de recursos, com `GET /{id}`, `GET /`, `PATCH /{id}` e `active=false`; **não realizar exclusão física** de entidade referenciada. Fornecedores são cadastro básico, sem portal/catálogo. Campos obrigatórios explicitados na tabela; `contactEmail?` omitido se ausente. Identificadores são gerados no servidor.

### 6.3 Fleet — P2

| Método e endpoint | Request | Response | HTTP | Autorização |
|---|---|---|---|---|
| `POST /api/v1/carriers` | `{name:string,active:boolean}` | `{id:UUID,name:string,active:boolean}` | 201,400,403,409 | ADMIN/OPERATOR |
| `POST /api/v1/drivers` | `{name:string,carrierId?:UUID,active:boolean}` | `{id:UUID,name:string,carrierId?:UUID,active:boolean}` | 201,400,403,404 | ADMIN/OPERATOR |
| `POST /api/v1/vehicles` | `{identifier:string,capacityKg:WeightKg,fleetType:FleetType,carrierId?:UUID,active:boolean}` | `{id:UUID,identifier:string,capacityKg:WeightKg,fleetType:FleetType,carrierId?:UUID,active:boolean}` | 201,400,403,404,409 | ADMIN/OPERATOR |
| `GET /api/v1/fleet/assignments/{routeId}` | path UUID | `AssignmentSnapshot` | 200,401,403,404 | ADMIN/OPERATOR |

`carrierId` obrigatório para frota terceirizada e omitido para própria. `identifier` é identificação local do veículo (1..40), única. Atribuição de rota é acionada pela API de rotas de P1 via porta interna; **não criar endpoint público paralelo para atribuição**. CRUD adicional segue padrão de partners. Não criar funcionalidades de controle de jornada ou GPS.

### 6.4 Orders e billing — P1

| Método e endpoint | Request | Response | HTTP | Autorização |
|---|---|---|---|---|
| `POST /api/v1/orders` | `CreateOrderRequest` | `OrderView` | 201,400,401,403,404 | ADMIN/OPERATOR/STORE própria |
| `GET /api/v1/orders/{orderId}` | UUID | `OrderView` | 200,401,404 | ADMIN/OPERATOR/STORE própria |
| `GET /api/v1/orders?page=0&size=20` | query | página `OrderView` | 200,401,403 | ADMIN/OPERATOR/STORE própria |
| `POST /api/v1/orders/{orderId}/confirm` | body `{}` | `OrderView` | 200,401,404,409,422,503 | ADMIN/OPERATOR/STORE própria |
| `POST /api/v1/orders/{orderId}/cancel` | `{reason:string}` | `OrderView` | 200,401,404,409 | ADMIN/OPERATOR/STORE própria, se elegível |
| `POST /api/v1/orders/{orderId}/freight-quote` | `{}` | `FreightQuoteView` | 200,401,404,422,503 | ADMIN/OPERATOR/STORE própria |

```json
{"storeId":"22222222-2222-4222-8222-222222222222","originId":"11111111-1111-4111-8111-111111111111","destination":{"line1":"Rua Destino, 200","line2":null,"city":"Cidade Fictícia","state":"SC","postalCode":"00000-000","country":"BR","latitude":-26.490000,"longitude":-49.080000},"recipientName":"Cliente Fictício","recipientEmail":"cliente@example.invalid","items":[{"description":"Produto fictício","quantity":2,"unitWeightKg":"20.000"}]}
```

`CreateOrderRequest`: todos obrigatórios exceto `recipientEmail?`; `items` 1..100, `description` 1..200, `quantity` inteiro 1..10000, `unitWeightKg` decimal string >0. `storeId` de STORE deve ser igual ao do ator; P1 valida origem ativa e vinculada via P2. `OrderView` = `OrderSnapshot` + `items` + `createdAt`; `OrderView` em `DRAFT` pode ter `origin`/coordenadas pendentes, mas `CONFIRMED` não. `FreightQuoteView`:

```json
{"quoteId":"88888888-8888-4888-8888-888888888888","orderId":"44444444-4444-4444-8444-444444444444","distanceKm":"12.000","tariffPerKm":"2.50","amount":"30.00","currency":"BRL","calculatedAt":"2026-11-14T12:00:00.000Z","source":"OSRM"}
```

**ATENÇÃO:** `tariffPerKm:"2.50"` é valor **somente de fixture**, não tarifa aprovada para implantação. Em execução real ler configuração global. Arredondar valor final para 2 casas (`HALF_UP`); registrar distância e tarifa aplicadas. Se OSRM indisponível → 503, sem distância inventada. Distância manual apenas se aprovada explicitamente em regra de negócio e marcada como tal; não criar endpoint de override por padrão. Confirmação idempotente usa `orderId`, não duplica entrega nem pagamento simulado; entrega pode ser reconciliada se outbox falhar.

### 6.5 Routing — P1

| Método e endpoint | Request | Response | HTTP | Autorização |
|---|---|---|---|---|
| `POST /api/v1/routes/suggest` | `{originId:UUID,orderIds:UUID[1..10]}` | `{originId:UUID,orderedOrderIds:UUID[1..10],estimatedDistanceKm:DistanceKm}` | 200,400,403,409,503 | ADMIN/OPERATOR |
| `POST /api/v1/routes` | `{originId:UUID,orderIds:UUID[1..10]}` | `RouteSnapshot` | 201,400,403,409 | ADMIN/OPERATOR |
| `POST /api/v1/routes/{routeId}/assign` | `{}` | `RouteSnapshot` | 200,403,404,409 | ADMIN/OPERATOR |
| `POST /api/v1/routes/{routeId}/start` | `{}` | `RouteSnapshot` | 200,403,404,409 | ADMIN/OPERATOR |
| `GET /api/v1/routes/{routeId}` | UUID | `RouteSnapshot` | 200,401,403,404 | ADMIN/OPERATOR; DRIVER atribuído, somente leitura |
| `PUT /api/v1/routes/{routeId}/stops` | `{orderedOrderIds:UUID[1..10]}` | `RouteSnapshot` | 200,400,403,404,409 | ADMIN/OPERATOR, somente antes de iniciar |

`orderIds` sem duplicidade, todos pedidos confirmados, mesma origem, nenhum em outra rota ativa; ordem de array é ordem de paradas. `suggest` usa vizinho mais próximo e **não persiste**; `routes` persiste ordem recebida. `requiredCapacityKg` = soma `totalWeightKg` de pedidos. Iniciar exige motorista e veículo atribuídos; atualizar estado de entregas via porta. Se `FleetAssignmentPort` retornar erro, rota permanece sem atribuição. Se `DeliveryCommandPort` falhar, registrar reconciliação; não confirmar sucesso completo falsamente.

### 6.6 Deliveries e tracking — P3

| Método e endpoint | Request | Response | HTTP | Autorização |
|---|---|---|---|---|
| `GET /api/v1/deliveries/my` | `page,size` | página `DeliverySnapshot` | 200,401,403 | DRIVER autenticado |
| `PATCH /api/v1/deliveries/{deliveryId}/status` | `{status: "IN_TRANSIT"|"FAILED_ATTEMPT"|"ASSIGNED",reason?:string}` | `DeliverySnapshot` | 200,400,401,403,404,409 | DRIVER atribuído ou OPERATOR |
| `POST /api/v1/deliveries/{deliveryId}/complete` | `{receiverName:string}` | `DeliverySnapshot` | 200,400,401,403,404,409 | DRIVER atribuído ou OPERATOR |
| `GET /api/v1/tracking/{code}` | código público, sem JWT | `PublicTrackingView` | 200,404,429 | público; rate limit |

`reason` obrigatório e 1..500 para `FAILED_ATTEMPT`; proibido para outras transições. `receiverName` 1..150, não vazio. Transições permitidas: `PENDING→ASSIGNED`, `ASSIGNED→IN_TRANSIT`, `IN_TRANSIT→FAILED_ATTEMPT|DELIVERED`, `FAILED_ATTEMPT→ASSIGNED` (nova tentativa); `CANCELLED` somente segundo DAT/decisão pendente; `DELIVERED` terminal. Repetição do mesmo comando de conclusão com mesmo nome pode retornar snapshot existente; com nome diferente → 409 `CONFLICT`. Sem GPS ao vivo.

```json
{"status":"IN_TRANSIT","milestones":[{"status":"PENDING","at":"2026-11-14T09:00:00.000Z"},{"status":"ASSIGNED","at":"2026-11-14T10:00:00.000Z"},{"status":"IN_TRANSIT","at":"2026-11-14T12:00:00.000Z"}]}
```

`PublicTrackingView`: somente `status` e `milestones` (array cronológico de `{status,at}`); **proibido** `orderId`, `deliveryId`, `routeId`, destinatário, endereço, e-mail, telefone, preço, recebedor, motorista. Para código inexistente → 404 genérico, sem informar formato válido/existência. `trackingCode` deve ser gerado com CSPRNG e alto espaço de busca; limitar requisições por origem/IP em adaptador REST.

### 6.7 Notifications — P3

**Sem endpoint REST público de envio** no MVP: produtor chama `NotificationRequestPort.enqueue`. Evento válido:

```json
{"eventId":"99999999-9999-4999-8999-999999999999","eventType":"ORDER_CONFIRMED","orderId":"44444444-4444-4444-8444-444444444444","to":"cliente@example.invalid","occurredAt":"2026-11-14T12:00:00.000Z"}
```

Resposta lógica `{notificationId:UUID,accepted:true}`. `to` obrigatório; se pedido não tiver e-mail, não enfileirar e registrar resultado de não elegibilidade no domínio sem inventar destinatário. Outbox persiste `event_id`, `event_type`, destinatário, tentativas, próximo retry e status; `accepted=true` **não** garante envio. SMTP local Mailpit. Evento repetido idêntico retorna mesmo ID; evento com mesma chave e payload diferente → 409 lógico `IDEMPOTENCY_CONFLICT`.

### 6.8 Documents — P3

| Método e endpoint | Request | Response | HTTP | Autorização |
|---|---|---|---|---|
| `POST /api/v1/documents` | `{orderId:UUID,documentType:DocumentType}` | `DocumentReceipt` | 200 se já existe, 201 se novo; 400,401,403,404,422 | ADMIN/OPERATOR/STORE própria |
| `GET /api/v1/documents/{documentId}` | UUID | `DocumentReceipt` | 200,401,403,404 | ADMIN/OPERATOR/STORE própria |
| `GET /api/v1/documents/{documentId}/pdf` | UUID | bytes PDF (`application/pdf`) | 200,401,403,404 | ADMIN/OPERATOR/STORE própria |

Exemplo request `{"orderId":"44444444-4444-4444-8444-444444444444","documentType":"CTE_SIMULATED"}`; resposta `{"documentId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","orderId":"44444444-4444-4444-8444-444444444444","documentType":"CTE_SIMULATED","status":"GENERATED"}`. `DocumentRequestPort.ensureGenerated` não diferencia HTTP 200/201; controller verifica existência antes de chamar ou retorna 200 em ambos, mantendo idempotência. Tela e PDF devem conter **SIMULAÇÃO ACADÊMICA — SEM VALIDADE FISCAL**. Proibido SEFAZ, assinatura fiscal real ou documentos fiscalmente válidos.

### 6.9 Exemplos de requests inválidos

```json
{"routeId":"33333333-3333-4333-8333-333333333333","originId":"11111111-1111-4111-8111-111111111111","requiredCapacityKg":-40}
```

Inválido: peso não é string decimal e é negativo → `400 VALIDATION_ERROR` em adaptador REST; em porta Java `ContractException(VALIDATION_ERROR)`.

```json
{"status":"DELIVERED"}
```

Inválido para `PATCH /deliveries/{id}/status`: conclusão exige endpoint `/complete` e `receiverName` → `400 VALIDATION_ERROR`.

```json
{"originId":"11111111-1111-4111-8111-111111111111","orderIds":[]}
```

Inválido: rota exige 1..10 pedidos → `400 VALIDATION_ERROR`.

```json
{"orderId":"44444444-4444-4444-8444-444444444444","documentType":"NFE"}
```

Inválido: enum aceito é `NFE_SIMULATED|CTE_SIMULATED` → `400 VALIDATION_ERROR`.

## 7. Fixtures determinísticas e fakes

**Diretório canônico:** `contracts/v1/fixtures/`. Os exemplos JSON das seções 4–6 são a representação canônica; criar arquivos com **exatamente** os mesmos dados, sem IDs aleatórios nos testes. Sugestão de manifesto:

```text
contracts/v1/
  README.md
  schemas/                    # JSON Schema draft 2020-12 para REST/DTOs
  fixtures/
    actor-admin.json
    actor-store.json
    actor-driver.json
    origin-active.json
    origin-inactive.json
    order-confirmed.json
    order-draft.json
    route-assigned.json
    delivery-assigned.json
    assignment-success.json
    notification-order-confirmed.json
    document-cte-simulated.json
    tracking-in-transit.json
    error-validation.json
    error-not-found.json
  tests/                      # contract tests provider/consumer
```

**IDs fixos do conjunto base:** origem `11111111-1111-4111-8111-111111111111`; loja `22222222-2222-4222-8222-222222222222`; rota `33333333-3333-4333-8333-333333333333`; pedido `44444444-4444-4444-8444-444444444444`; motorista `55555555-5555-4555-8555-555555555555`; veículo `66666666-6666-4666-8666-666666666666`; entrega `77777777-7777-4777-8777-777777777777`; cotação `88888888-8888-4888-8888-888888888888`; evento `99999999-9999-4999-8999-999999999999`; documento `aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa`. Datas fixas `2026-11-14T12:00:00.000Z`. Coordenadas fictícias não estabelecem área de operação.

### Matriz de comportamento dos fakes (obrigatória)

| Fake | Sucesso | Negativo | Indisponibilidade | Duplicidade/concorrrência |
|---|---|---|---|---|
| Partner | origem ativa e loja vinculada | UUID ausente `NOT_FOUND`, origem inativa `active=false` | `DEPENDENCY_UNAVAILABLE` injetável | leitura repetida idêntica |
| Fleet assignment | devolve motorista/veículo fixos e reserva | `NO_ELIGIBLE_RESOURCE`, capacidade inválida | `DEPENDENCY_UNAVAILABLE` | mesma rota retorna mesmo assignment; duas rotas não recebem mesmo veículo simultaneamente |
| Fleet release | libera reserva existente | rota ausente `NOT_FOUND` | `DEPENDENCY_UNAVAILABLE` | mesmo eventId retorna `released=false` |
| Order snapshot | pedido confirmado fixture | pedido ausente; draft para precondition | `DEPENDENCY_UNAVAILABLE` | lookup repetido igual |
| Route snapshot | rota atribuída fixture | rota inexistente, sem motorista | `DEPENDENCY_UNAVAILABLE` | snapshot repetido igual |
| Delivery command | cria uma entrega e atribui rota | pedido não confirmado; rota incompatível | `DEPENDENCY_UNAVAILABLE` | chamadas repetidas não duplicam entrega/evento |
| Delivery status | status fixo com timestamp | pedido inexistente | `DEPENDENCY_UNAVAILABLE` | leitura repetida igual |
| Notification | `accepted=true`, outbox em memória | e-mail inválido, evento conflitante | persistência fake indisponível | mesmo `eventId+eventType` devolve ID original |
| Document | PDF simulado com watermark | tipo inválido, frete ausente | gerador PDF indisponível | mesmo pedido/tipo devolve ID original |
| Current actor | admin/store/driver fixtures | ausência `UNAUTHENTICATED`, loja errada `FORBIDDEN` | não aplicável | contexto estável durante caso de uso |

**Implementação:** fakes com `ConcurrentHashMap`, relógio injetável `Clock.fixed(...)`, UUIDs constantes, falhas injetadas por configuração de teste, sem chamadas HTTP/SMTP/OSRM. Não usar `Thread.sleep` para testar retries. Testar com fake e com adaptador real contra o mesmo conjunto de cenários. Nunca habilitar fake auth em perfil de integração/prod. **Mocks não substituem implementação real na Sprint 4.**

## 8. Banco de dados, integridade e Flyway

| Módulo proprietário | Tabelas próprias | Integridade obrigatória |
|---|---|---|
| `identity` P1 | `users` | `id UUID PK`, email único, hash senha, role, `store_id?`, `driver_id?` como IDs opacos |
| `orders` P1 | `orders`, `order_items` | `orders.id UUID PK`, `store_id`, `origin_id`, `tracking_code UNIQUE`, snapshot endereço; FK **somente dentro do módulo** |
| `billing` P1 | `freight_quotes`, `simulated_payments` | `order_id` indexado; no máximo 1 pagamento simulado aprovado por pedido; cotação snapshot imutável |
| `routing` P1 | `routes`, `route_stops` | `route_id` + posição única, `order_id` não repetido em rota; exclusividade de rota ativa garantida na aplicação/transação |
| `partners` P2 | `stores`, `suppliers`, `distribution_centers` (se tabela separada) | UUID PK; origem vinculada a loja; soft delete/inativação |
| `fleet` P2 | `carriers`, `drivers`, `vehicles`, `fleet_assignments` | índices por `route_id UNIQUE`; reserva ativa de motorista/veículo sem duplicidade; bloqueio concorrente |
| `deliveries` P3 | `deliveries`, `delivery_events` | `deliveries.order_id UNIQUE`; eventos append-only, ordenados por timestamp/id |
| `notifications` P3 | `notification_outbox` | `UNIQUE(event_id,event_type)`; status, retries, payload hash |
| `documents` P3 | `simulated_documents` | `UNIQUE(order_id,document_type)` |

**Observação:** `distribution_centers`, `fleet_assignments` são decisões **técnicas de persistência**, não funcionalidades adicionais. P2 pode implementar centros de distribuição em tabela de origens unificada, desde que `OriginSnapshot` seja idêntico. `fleet_assignments` pode ser representada por tabela de reservas equivalente; não alterar contrato externo.

**PostgreSQL:** PK UUID; `TIMESTAMPTZ` para datas; `NUMERIC(12,2)` para dinheiro, `NUMERIC(12,3)` para kg/km; `VARCHAR` com limites; índices para lookup; restrições únicas para idempotência. **Não criar FK física cruzando schemas/módulos**: validar existência via portas e usar IDs opacos. Evitar cascade delete entre módulos. Cada módulo é dono exclusivo de suas tabelas, consultas e migrações.

**Schemas integrados recomendados:** `identity`, `orders`, `billing`, `routing`, `partners`, `fleet`, `deliveries`, `notifications`, `documents` (mesmo banco PostgreSQL). O DAT usa nomes de tabelas sem prefixo: `schema.table` preserva esses nomes. Bancos locais podem usar os mesmos schemas, isolados por workspace. Em Supabase, usar conexão PostgreSQL para backend, não expor credenciais no frontend.

**Migrações:** cada workspace mantém `db/migration/<module>/` com migrações locais e IDs locais; antes do merge, integração P1 compõe uma **ordem global estável** para Flyway na aplicação integrada. Após uma migração ser aplicada em `develop`, **nunca** renomear, editar ou reutilizar versão; mudanças corretivas entram como migração nova. Check CI: banco limpo → migrate → validar schemas/índices → testes; banco com histórico → migrate sem perda de dados. Não permitir três agentes escreverem simultaneamente no mesmo `db/migration` global.

## 9. Testes de contrato e critérios de aceite

**Estratégia dupla:** (1) JSON Schema Draft 2020-12 para requests/responses REST e fixtures; (2) testes JUnit 5 de interface/porta executados contra `FakeX` e `RealX` com a mesma suíte abstrata. Opcionalmente Spring MockMvc para controllers. Schemas devem marcar `required`, `additionalProperties:false` em requests, `format:uuid`, `format:date-time`, padrões decimais `^\\d+\\.\\d{2}$` dinheiro e `^\\d+\\.\\d{1,3}$` peso/km, enum estrito, limites de array e `oneOf` para campos nullable. Validar exemplos válidos e inválidos em CI.

| Contrato | Positivos obrigatórios | Negativos obrigatórios | Critério de aceite |
|---|---|---|---|
| PartnerLookup | origem ativa; centro distribuição ligado a loja | ID inexistente, origem inativa, vínculo incorreto | snapshot completo e estável; nenhuma consulta JPA alheia |
| FleetAssignment | própria prioritária, fallback terceirizada, desempate | capacidade, ocupação, falta de elegíveis, concorrência | idempotência e reserva atômica sem duplicação |
| FleetRelease | liberação após conclusão | rota inexistente, cancelamento não aprovado | repetição não libera outra rota |
| OrderSnapshot | lookup por ID e código, snapshot imutável | ID/código inexistente, draft | DTO exato, tracking não exposto em público |
| RouteSnapshot | planejada/atribuída/iniciada | ID ausente, driver nulo antes de atribuir | nullabilidade e estados coerentes |
| DeliveryCommand | criação única, atribuição única | pedido não confirmado, rota errada, duplicidade | `order_id UNIQUE`, eventId idempotente |
| DeliveryStatus | leitura de todos estados | inexistente, transição inválida via caso de uso | status consistente, evento imutável |
| NotificationRequest | enfileira, retry SMTP, captura Mailpit | e-mail inválido, falha outbox, payload conflitante | mesmo evento não duplica envio lógico |
| DocumentRequest | ambos tipos, PDF com watermark | tipo inválido, sem frete, não autorizado | 1 documento por pedido/tipo; sem validade fiscal |
| CurrentActor | ADMIN, OPERATOR, STORE, DRIVER | JWT ausente/expirado, cross-store, outro motorista | fake somente dev; regras por objeto no caso de uso |
| REST global | 2xx com schema correto | 400,401,403/404,409,422,429,503 | todos erros no ProblemDetail canônico |

**Execução isolada:** cada pessoa copia `contracts/v1` congelado para seu workspace ou usa módulo compartilhado versionado, executa testes com `-Pcontract-test`/perfil equivalente **sem** servidores dos outros. Os testes de provedor usam o adaptador real local e o mesmo dataset. Se o contrato de consumidor não corresponder ao de provedor, PR falha. Não usar snapshots JSON diferentes por agente. Testes de autorização com ator falso para STORE/DRIVER e objetos de outra loja/motorista; 404 para recursos fora de escopo.

## 10. Protocolo de integração e Git

**Branches por Sprint:** `feat/p1-sprint-N`, `feat/p2-sprint-N`, `feat/p3-sprint-N` (N=1..4), cada uma criada a partir de `origin/develop` atualizado. **Uma branch por pessoa por Sprint**, commits frequentes `feat(scope): ...`, `fix(scope): ...`, `test(scope): ...`; push na própria branch; PR ao fim da Sprint para `develop`; `main` recebe apenas release validada. Não trocar de branch no meio da Sprint. **Auto-merge** apenas com CI e revisões obrigatórias satisfeitas; PR com conflito/contrato divergente fica bloqueado. Não permitir agente alterar `contracts/v1` sem aprovação humana.

### Procedimento operacional obrigatório (Sprint 4 e PRs de integração)

1. **Inventário:** verificar hashes/versão de `contracts/v1` em todos workspaces; comparar DTOs e schemas; rejeitar divergência.
2. **Contract tests isolados:** executar suíte de cada provedor contra fakes e implementações reais, com schemas JSON.
3. **Substituição controlada:** trocar `FakePartnerLookup` etc. por adaptadores reais via DI/configuração; nenhum fake de auth habilitado. Testar startup com providers reais.
4. **Composição do backend:** consolidar módulos sem importar entidades JPA alheias; validar ausência de dependências circulares de compilação. A integração é por portas Java no monólito.
5. **Banco:** aplicar migrações globais em PostgreSQL limpo e banco com histórico; verificar uniques, índices, schemas e dados demo; proibir scripts destrutivos.
6. **Frontend:** integrar rotas/telas Vue de cada workspace ao shell P1; verificar permissões, importações e client HTTP `/api/v1`.
7. **E2E:** ADMIN/OPERATOR cadastra loja/origem, frota, pedido; cota frete; confirma pagamento simulado; cria/atribui/inicia rota; DRIVER registra tentativa e conclusão; público acompanha código; Mailpit captura e-mails; PDF simulado tem marca d'água.
8. **Falhas:** repetir confirmação/atribuição/notificação/documento, simular OSRM/SMTP indisponível, negar cross-store e outro motorista; verificar recuperação sem duplicação.
9. **CI PR:** `mvn test`/build backend, `npm ci && npm run build` frontend, contract tests, Flyway em banco limpo, verificações de segurança básica e `.env` ausente; revisão humana de contratos/migrações/autorização.
10. **Aprovação:** somente merge em `develop` quando todos checks obrigatórios verdes, zero erros de schema, sem conflitos, contratos v1 preservados, testes de isolamento e E2E essenciais aprovados. Após validação coletiva, PR `develop→main`.

### Matriz de dependências de runtime (não bloqueia desenvolvimento)

| Produtor | Consumidor | Modo Sprint 1–3 | Modo integrado Sprint 4 |
|---|---|---|---|
| P2 Partner | P1 Orders/Routing | FakeOrigin/Store | Adapter Partner real |
| P2 Fleet | P1 Routing | FakeFleetAssignment | Adapter Fleet real |
| P1 Route | P2 Fleet / P3 Deliveries | FakeRouteSnapshot | Adapter Route real |
| P1 Order | P3 Deliveries/Documents/Tracking | FakeOrderSnapshot | Adapter Order real |
| P3 Delivery | P1 Orders/Routing | FakeDeliveryCommand/Status | Adapter Delivery real |
| P3 Notifications | P1 Orders / P3 Deliveries | FakeNotificationOutbox | Adapter Notification real |
| P3 Documents | P1 UI/Orders | FakeDocumentRequest | Adapter Document real |
| P1 Identity | P2/P3 casos de uso | FakeCurrentActor `dev` | Adapter JWT real |
| P2 FleetRelease | P1 Routing | FakeFleetRelease | Adapter FleetRelease real |

## 11. Alterações exatas recomendadas nos arquivos de Sprint (NÃO aplicadas)

**Em TODOS os três `SPRINTS_PESSOA_*.md`:**
- Na seção `Contrato de independência`, item 4, substituir `{code,message,details}` por `ProblemDetail` normativo da seção 3 deste arquivo; manter `/api/v1`, JSON, UUID, UTC e BigDecimal.
- Em `Contratos mínimos congelados (v1)` e `Contratos v1 congelados para independência`, substituir a tabela resumida pelas assinaturas e schemas das seções 4–5; corrigir `requiredCapacity`→`requiredCapacityKg`, `PartnerLookupPort` em duas operações, nullabilidade, tipos de dinheiro como strings JSON.
- Em `Padrões compartilhados obrigatórios`, acrescentar `CONTRACTS_V1.md` como única fonte normativa e exigir contract tests contra os mesmos fixtures.
- Em `Fonte de verdade`, indicar que nenhum agente pode modificar `contracts/v1` sem aprovação humana e que diferenças de contratos bloqueiam PR.

**`SPRINTS_PESSOA_1_50.md`:**
- Na seção `Decisões específicas — Pessoa 1`, atualizar `/api/auth/*`, `/api/orders/*`, `/api/routes/*` para `/api/v1/...` e acrescentar `/api/v1/orders/{id}/freight-quote`, `PUT /api/v1/routes/{id}/stops` conforme seção 6.
- Em `orders`, explicitar chamada a `DeliveryCommandPort.ensureCreated` com idempotência `orderId/eventId`, e publicação de `NotificationRequestPort.enqueue` para confirmação; registrar reconciliação em falhas.
- Em `routing`, incluir `DeliveryCommandPort.assignRoute` e `FleetReleasePort.release` após conclusão de rota; nunca chamar SQL alheio.
- Em `billing`, fixar serialização JSON de dinheiro como string e configuração inicial de tarifa **pendente**.
- Em `Sprint 1`, adicionar fake de `DeliveryCommandPort` e `FleetReleasePort`; em `Sprint 2`, usar `RouteSnapshot` canônico.

**`SPRINTS_PESSOA_2_25.md`:**
- Em `partners`, garantir `OriginSnapshot.storeId` obrigatório para loja e centro de distribuição, e expor `getOrigin`/`getStore`.
- Em `fleet`, trocar peso por `requiredCapacityKg` string decimal JSON / `BigDecimal` Java; acrescentar `FleetReleasePort` e idempotência por `routeId/eventId`.
- Em `Sprint 1–2`, adicionar testes de concorrência e `RouteSnapshotPort` fake com status/origem; em `Sprint 4`, integrar liberação de reservas por evento.

**`SPRINTS_PESSOA_3_25.md`:**
- Em `deliveries`, acrescentar `DeliveryCommandPort.ensureCreated/assignRoute`, `OrderSnapshotPort.findByTrackingCode`, e `DeliverySnapshot` com `routeId?`.
- Nos endpoints, adicionar `/api/v1` e especificar `PATCH status` separado de `POST complete`; manter `receiverName` obrigatório e `reason` em falha.
- Em `notifications`, adotar `NotificationRequestPort` idempotente por `eventId+eventType` e resposta de aceite, não envio.
- Em `documents`, acrescentar `POST /api/v1/documents` e unicidade por `(orderId,documentType)`; garantir PDF e tela com watermark.
- Em `Sprint 1`, adicionar fake de `DeliveryCommandPort` para teste do consumidor; em `Sprint 3`, testar ausência de e-mail e retry SMTP.

## 12. Checklist final de auditoria

- [x] Os **nove contratos internos** pedidos/adicionais têm dono e consumidor identificados: `PartnerLookup`, `FleetAssignment`, `FleetRelease`, `OrderSnapshot`, `RouteSnapshot`, `DeliveryCommand`, `DeliveryStatus`, `NotificationRequest`, `DocumentRequest`, além de `CurrentActor` (**10 interfaces**).
- [x] Tipos, nullabilidade, enums, idempotência e erros comuns foram padronizados.
- [x] Há exemplos válidos, inválidos, cenários de falha e matriz de fakes para cada porta.
- [x] Dependências de **código** são apenas em `contracts/v1`; não há ciclo de importação obrigatório.
- [x] Desenvolvimento isolado por três workspaces é viável com banco e fakes locais.
- [x] O protocolo de substituição de fakes, migrações, testes e PR está especificado.
- [ ] **Ainda requer implementação/verificação em código:** JSON Schemas reais, classes Java, testes executáveis, CI e compatibilidade de builds — este documento não os substitui.
- [ ] **Ainda requer decisões humanas:** itens de negócio pendentes da seção 1.

**Regra de entrega:** cada agente deve apresentar no PR evidências de testes de contrato e uma lista de portas reais/fakes em uso. Não marcar integração concluída enquanto houver fake de dependência intermodular no perfil integrado.
