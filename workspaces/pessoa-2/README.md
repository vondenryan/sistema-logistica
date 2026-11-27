# Workspace Pessoa 2

Workspace de parceiros e frota com Java 21, Spring Boot 3.5.7, Vue 3, PostgreSQL 16 e Flyway. A [integração da Sprint 4](INTEGRATION.md#sprint-4) conecta os módulos ao núcleo de pedidos e rotas.

## Requisitos

- Java 21
- Docker Desktop com Docker Compose
- Node.js 20.19+ ou 22.12+

## Banco local

Os nomes e a porta do PostgreSQL são exclusivos deste workspace: container `sistema-logistica-pessoa2-postgres`, banco `pessoa2_db`, volume `sistema_logistica_pessoa2_pgdata` e porta `55432` (Pessoa 3 usa `5434`).

```powershell
Copy-Item .env.example .env
docker compose up -d --wait
```

O Flyway aplica `backend/src/main/resources/db/migration` ao iniciar a aplicação. A migration `V2` cria a tabela de lojas, com índice único case-insensitive para o nome.

## Build

```powershell
Set-Location backend
.\mvnw.cmd clean verify
Set-Location ..\frontend
npm ci
npm run build
```

Para executar o backend isolado, ative explicitamente o perfil `dev` (que fornece um ator ADMIN fixo somente para desenvolvimento) com `.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev`. Em outro terminal, inicie o frontend com `npm run dev`. Para parar o banco sem remover o volume, execute `docker compose down`.

## Cadastro de lojas

O backend expõe `GET /api/v1/stores?page=0&size=20`, `GET /api/v1/stores/{storeId}`, `POST /api/v1/stores` e `PATCH /api/v1/stores/{storeId}`. A listagem usa paginação (tamanho de 1 a 100) e ordenação estável por criação e ID. Os requests usam `{ "name": "...", "active": true }`; `active` é obrigatório no POST. O PATCH aceita `name`, `active` ou ambos. Para inativar uma loja, envie `{"active":false}`; não há exclusão física.

Nomes são aparados e únicos sem distinção entre maiúsculas e minúsculas; criar ou renomear para um nome existente retorna HTTP 409 `CONFLICT`. `ADMIN` e `OPERATOR` podem listar, criar e atualizar; `STORE` só pode consultar a própria loja. Erros de validação e domínio seguem RFC 9457 `ProblemDetail`, com `code` e `details`, conforme `docs/CONTRACTS_V1.md`.

O fake `CurrentActorPort` está disponível somente no perfil `dev`. Fora desse perfil, este workspace ainda não integra JWT e responde 401 `UNAUTHENTICATED` aos endpoints protegidos; não habilite o fake em ambientes compartilhados ou de produção.

## Contratos compartilhados

`PartnerLookupPort` e seus snapshots usam o pacote Java compartilhado `contracts.v1`, compatível com os demais workspaces; as coordenadas do snapshot são `BigDecimal`. As portas de atribuição, liberação e consulta de rotas também usam `contracts.v1`. Não há uma pasta compartilhada `contracts/v1/` na raiz do repositório.

## Cadastro de origens

O backend expõe `GET /api/v1/origins?page=0&size=20`, `GET /api/v1/origins/{originId}`, `POST /api/v1/origins` e `PATCH /api/v1/origins/{originId}`. A coleção segue a paginação de lojas. A origem retorna `{id,type,storeId,address,active}`; `type` é `STORE` ou `DISTRIBUTION_CENTER`, e ambos exigem um `storeId` já existente. O endereço segue `AddressSnapshot` em `docs/CONTRACTS_V1.md`. O PATCH aceita `type`, `storeId`, `address` (objeto completo) e/ou `active`; use `{"active":false}` para inativar, sem exclusão física.

Somente `ADMIN` e `OPERATOR` podem listar, criar ou atualizar origens. `STORE` pode consultar somente origens vinculadas à sua loja; tentativa de acesso a outra origem retorna 404. `DRIVER` recebe 403. Requests desconhecidos são rejeitados, e erros usam RFC 9457 `ProblemDetail`.

**Ponto em aberto:** o contrato não define se uma origem pode estar vinculada a uma loja inativa. Essa regra não é aplicada pelo cadastro e requer decisão de negócio posterior.

As credenciais do `.env.example` são somente para desenvolvimento local; não use `.env` em ambientes compartilhados ou de produção.

## Cadastro de fornecedores

O backend expõe `GET /api/v1/suppliers?page=0&size=20`, `GET /api/v1/suppliers/{supplierId}`, `POST /api/v1/suppliers` e `PATCH /api/v1/suppliers/{supplierId}`. A paginação replica o padrão de lojas e origens, com tamanho de 1 a 100 e ordenação estável por criação e ID.

O POST recebe `{name,contactEmail?,storeId}`. `name` é obrigatório (máximo 160 caracteres), `contactEmail` é opcional (máximo 254 caracteres), e `storeId` deve apontar para uma loja existente. A resposta contém `{id,name,contactEmail?,storeId}` e omite `contactEmail` quando ausente. Identificadores são gerados no servidor.

Somente `ADMIN` e `OPERATOR` podem listar, criar ou atualizar fornecedores. `STORE` recebe 403 na listagem, criação e atualização e só consulta fornecedores vinculados à própria loja; outro fornecedor fora do escopo retorna 404. `DRIVER` recebe 403.

**Pontos em aberto/decisões locais:** o contrato não define como `active` aparece nas respostas de fornecedor, portanto esta implementação não expõe `active`; a inativação com `{"active":false}` fica interna e não exclui fisicamente nem deixa o fornecedor invisível. O PATCH não consegue apagar `contactEmail`: `contactEmail:null` é rejeitado com 400 e o campo omitido preserva o valor atual. IDs inválidos em GET/PATCH retornam 400 `VALIDATION_ERROR`.

## Cadastros de frota

O backend expõe `GET /api/v1/carriers`, `GET /api/v1/carriers/{carrierId}`, `POST /api/v1/carriers` e `PATCH /api/v1/carriers/{carrierId}`; as mesmas operações existem em `/api/v1/drivers` e `/api/v1/vehicles`. As listagens usam `page=0` e `size=20` por padrão, com tamanho de 1 a 100 e ordenação estável por criação e ID. `active` aparece sempre nas respostas; `carrierId` é omitido quando não há vínculo. UUID inválido em GET/PATCH retorna 400 `VALIDATION_ERROR`.

Requests:

- Carrier: `{ "name": "...", "active": true }`.
- Driver: `{ "name": "...", "carrierId": "<UUID>", "active": true }`; `carrierId` pode ser omitido. Quando informado, deve referenciar uma transportadora existente (404).
- Vehicle: `{ "identifier": "VEI-001", "capacityKg": "1000.000", "fleetType": "OWN", "active": true }`. `identifier` tem de 1 a 40 caracteres e é único. `capacityKg` é uma string decimal positiva, com até três casas e compatível com `decimal(12,3)`. Para `THIRD_PARTY`, `carrierId` é obrigatório e deve existir (404); para `OWN`, deve ser omitido (enviá-lo retorna 400).

PATCH aceita apenas campos reconhecidos; campo omitido mantém o valor atual e `carrierId:null` retorna 400 também para motoristas. Ao mudar um veículo para `OWN`, o vínculo anterior é limpo automaticamente; para `THIRD_PARTY`, deve haver uma transportadora existente. Use `{"active":false}` para inativar, sem exclusão física. Somente `ADMIN` e `OPERATOR` podem operar os três cadastros; outros papéis recebem 403 e ausência de identidade recebe 401.

As únicas condições de 409 definidas para estes cadastros são:

- Carrier: nome duplicado após aparar espaços e comparar sem diferenciar maiúsculas/minúsculas (POST ou PATCH).
- Vehicle: `identifier` duplicado (POST ou PATCH).
- Driver: não há condição de conflito 409 definida.

O seed de frota é idempotente, insere cada registro somente se seu ID ainda não existir e é carregado apenas nos perfis `dev` e `test`, nunca no perfil padrão. Todos os registros são ativos. Os IDs `55555555-5555-4555-8555-555555555555` (motorista) e `66666666-6666-4666-8666-666666666666` (veículo) são os IDs base especificados no contrato; os demais IDs abaixo são locais deste seed:

| Tipo | ID | Nome/identificador | Tipo de frota/capacidade | Transportadora |
|---|---|---|---|---|
| Carrier | `77777777-7777-4777-8777-777777777777` | Transportadora Parceira Alfa | — | — |
| Carrier | `88888888-8888-4888-8888-888888888888` | Transportadora Parceira Beta | — | — |
| Driver | `55555555-5555-4555-8555-555555555555` | Motorista Próprio 1 | — | — |
| Driver | `55555555-5555-4555-8555-555555555556` | Motorista Próprio 2 | — | — |
| Driver | `55555555-5555-4555-8555-555555555557` | Motorista Terceirizado 1 | — | Alfa |
| Vehicle | `66666666-6666-4666-8666-666666666666` | VEI-001 | OWN / `1000.000` kg | — |
| Vehicle | `66666666-6666-4666-8666-666666666667` | VEI-002 | OWN / `500.000` kg | — |
| Vehicle | `66666666-6666-4666-8666-666666666668` | TER-001 | THIRD_PARTY / `2000.000` kg | Alfa |

**Ponto em aberto do contrato:** não está definido como a inativação de uma transportadora afeta motoristas e veículos vinculados; por isso, o cadastro não bloqueia alterações nem muda a visibilidade dos vínculos com base no estado da transportadora.

## Atribuição automática de frota

O backend implementa `FleetAssignmentPort` e `FleetReleasePort` internamente. A consulta administrativa é `GET /api/v1/fleet/assignments/{routeId}` (ADMIN/OPERATOR). Para demonstração local, `POST /api/v1/fleet/assignments` aceita `{ "routeId": "<UUID>", "originId": "<UUID>", "requiredCapacityKg": "40.000" }` e existe **somente no perfil `dev`**, também restrita a ADMIN/OPERATOR; ela chama a mesma porta interna. Não há endpoint público de atribuição fora desse perfil.

O fake local de `RouteSnapshotPort` existe apenas em `dev` e `test`, não consulta tabelas de `routing` e fornece cinco rotas PLANNED fixas de IDs `33333333-3333-4333-8333-333333333333` a `33333333-3333-4333-8333-333333333337`, todas para a origem `11111111-1111-4111-8111-111111111111` e a loja `22222222-2222-4222-8222-222222222222`; também fornece fixtures fixas ASSIGNED e COMPLETED para validar pré-condições. As rotas `...3334` e `...3335` ficam reservadas aos testes de concorrência. Um fixture local idempotente disponibiliza a loja e a origem base pelo `PartnerLookupPort`. No modo isolado, o snapshot v1 de rota não contém o peso total. No perfil integrado, a frota valida a soma dos pesos dos pedidos confirmados pela porta de pedidos.

A seleção usa somente motoristas e veículos ativos e sem reserva ativa, capacidade suficiente, frota própria primeiro e fallback terceirizado; pares terceirizados precisam pertencer à mesma transportadora. Entre candidatos, motoristas são ordenados pelo número de reservas ativas no próprio fleet (decisão provisória para representar “entregas pendentes”) e depois por UUID; veículos empatados também são ordenados por UUID. Não havendo par elegível, a porta retorna `NO_ELIGIBLE_RESOURCE` (409). A migration V6 mantém uma única linha por rota e protege motorista/veículo contra reservas ativas concorrentes.

`FleetReleasePort.release` deduplica por `eventId`. `ROUTE_CANCELLED` retorna `PRECONDITION_FAILED` (422), pois a regra de cancelamento ainda não foi aprovada. Liberar uma reserva mantém a linha da atribuição, e `route_id` continua único: a mesma rota não recebe uma nova atribuição após a liberação.

## Demonstração isolada e integração

Consulte [INTEGRATION.md](INTEGRATION.md) para iniciar o workspace sem outros serviços, executar o roteiro de CRUD e atribuição com seed/fakes, e acompanhar separadamente as evidências de validação local e CI.
