# Integração isolada — Pessoa 2

Este workspace pode demonstrar parceiros e frota sem iniciar os serviços dos outros workspaces. Os dados e IDs abaixo são fictícios e locais.

## Fronteiras dos contratos

- `PartnerLookupPort` é implementada pelo adaptador real de parceiros da Pessoa 2 e devolve snapshots v1 sem entidades JPA.
- `FleetAssignmentPort` e `FleetReleasePort` são implementadas internamente pela Pessoa 2.
- `RouteSnapshotPort` usa `FakeRouteSnapshotAdapter` somente nos perfis `dev` e `test`. Ele contém rotas determinísticas, devolve `NOT_FOUND` para IDs ausentes e fornece estados `PLANNED`, `ASSIGNED` e `COMPLETED`.
- `CurrentActorPort` usa ator local somente no perfil `dev`; a identidade JWT central não faz parte desta demonstração.
- A criação de atribuição de demonstração é feita pelo endpoint existente `POST /api/v1/fleet/assignments`, disponível somente em `dev` e protegido para `ADMIN`/`OPERATOR`. Não é um endpoint público paralelo ao fluxo de rotas.
- O `RouteSnapshot` v1 não inclui peso total da rota. A atribuição valida o peso positivo solicitado contra a capacidade dos veículos elegíveis; não compara com pedidos nem infere uma capacidade da rota.

## Iniciar localmente

Pré-requisitos: Java 21, Node.js 20.19+ ou 22.12+, Docker Desktop e Docker Compose.

Na raiz do repositório, preparar e iniciar o PostgreSQL isolado:

```powershell
Set-Location workspaces\pessoa-2
Copy-Item .env.example .env
docker compose up -d --wait
```

Iniciar o backend em um terminal:

```powershell
Set-Location workspaces\pessoa-2\backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

Iniciar o frontend em outro terminal:

```powershell
Set-Location workspaces\pessoa-2\frontend
npm ci
npm run dev
```

Abrir o endereço local mostrado pelo Vite. O proxy `/api` aponta para o backend em `http://localhost:8082`. O ator `ADMIN` é o padrão no perfil dev. O seletor de perfil do frontend permite testar também `OPERATOR` e `STORE`; esses headers são exclusivos de desenvolvimento.

O seed de frota idempotente é carregado nos perfis `dev` e `test`. Entre os registros de demonstração estão veículos próprios com capacidades de `1000.000` kg e `500.000` kg, veículo terceirizado com capacidade de `2000.000` kg e seus motoristas/transportadora vinculados.

## Roteiro da demonstração

1. Abrir **Transportadoras**, **Motoristas** e **Veículos**. Consultar os registros seed e, se necessário, criar, editar ou inativar um registro pelos formulários. A inativação não exclui fisicamente o cadastro.
2. Abrir **Atribuição de frota**. Para uma demonstração repetível, usar uma rota PLANNED ainda não atribuída, por exemplo `33333333-3333-4333-8333-333333333333`. A origem fixa é `11111111-1111-4111-8111-111111111111`.
3. Informar `40.000` kg e acionar **Atribuir rota**. Com o seed intacto, a seleção prefere um par de frota própria. Uma solicitação acima da capacidade própria pode usar um par terceirizado elegível; acima de `2000.000` kg não há recurso seed elegível e a porta retorna `NO_ELIGIBLE_RESOURCE`.
4. Conferir os dados da atribuição e consultar novamente pelo mesmo `routeId`. A repetição com o mesmo payload é idempotente; payload diferente para a rota retorna `IDEMPOTENCY_CONFLICT`.
5. Repetir com outro ID de rota PLANNED se precisar de uma nova atribuição. `route_id` é único mesmo depois da liberação, portanto a mesma rota não pode receber outra atribuição.

Rotas PLANNED fixas disponíveis: `33333333-3333-4333-8333-333333333333` a `33333333-3333-4333-8333-333333333337`. As rotas `...3334` e `...3335` ficam reservadas aos testes de concorrência. O fake também contém fixtures `ASSIGNED` (`...3338`) e `COMPLETED` (`...3339`) para pré-condições.

## Testes e evidências

Backend, com PostgreSQL local disponível:

```powershell
Set-Location workspaces\pessoa-2\backend
.\mvnw.cmd --batch-mode clean verify
```

Frontend:

```powershell
Set-Location workspaces\pessoa-2\frontend
npm test
npm run build
```

| Execução | Resultado |
|---|---|
| Local — suíte completa backend + PostgreSQL | `.\mvnw.cmd --batch-mode -DfailIfNoTests=true test` com PostgreSQL 16 (`docker compose up -d --wait`) — **91 testes passaram** (0 falhas, 0 erros, 0 ignorados), incluindo `FleetContractJsonTests`, `FakeRouteSnapshotAdapterTests`, `PartnerLookupAdapterTests`, `PartnerLookupPortContractTests`, `OriginControllerContractTests`, `FleetControllerContractTests`, `FleetAssignmentIntegrationTests` e testes de persistência/autorização. |
| Local — frontend | `npm ci && npm test && npm run build` — **16 arquivos / 51 testes passaram**; build `vue-tsc -b && vite build` aprovado. |
| CI — workflow `backend-ci.yml` | Será validado pelo GitHub Actions ao abrir o PR `feat/p2-sprint-3` → `develop`. |

Os testes de contrato usam fixtures JSON v1, exercitam a serialização do `PartnerLookupPort`, `RouteSnapshotPort` e `FleetAssignmentPort`, e cobrem a tabela estática do fake de rotas. A suíte de integração cobre atribuição, capacidade, idempotência, conflito, liberação e concorrência com PostgreSQL 16 local, sem depender de serviços da Pessoa 1 ou Pessoa 3.

## Integração futura

Na Sprint 4, substituir o fake local de `RouteSnapshotPort` pelo adaptador da Pessoa 1 e ligar `CurrentActorPort` à identidade central, sem importar entidades ou repositórios de outros módulos. Esta demonstração não realiza essas integrações e não altera `docs/CONTRACTS_V1.md`.
