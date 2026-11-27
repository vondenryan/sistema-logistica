# Integração — Pessoa 2

## Sprint 4

O perfil Maven `integration` monta um backend Spring Boot 3.5.7 com os módulos existentes de identidade, pedidos, rotas, parceiros, frota e entregas. A composição usa as portas Java v1. O contexto JWT vem da identidade da Pessoa 1, os snapshots vêm das rotas/pedidos reais e a frota grava no mesmo PostgreSQL. A atribuição entra por `POST /api/v1/routes/{routeId}/assign`; a consulta administrativa usa `GET /api/v1/fleet/assignments/{routeId}`.

A capacidade é conferida contra os pedidos confirmados da rota. Uma trava transacional no banco serializa as reservas e as liberações. Os índices únicos de motorista e veículo ativos continuam protegendo a integridade. A porta de liberação recebe o motivo e o evento v1 e consulta o estado real da rota. A emissão do evento de conclusão ainda depende do núcleo de rotas.

### Executar com PostgreSQL

A partir de `workspaces/pessoa-2`, crie um banco PostgreSQL de integração e exporte `DB_URL` (JDBC), `DB_USER`, `DB_PASSWORD` e `JWT_SECRET` (mínimo de 32 bytes). Para cotar frete, configure também `OSRM_BASE_URL` e `FREIGHT_TARIFF_PER_KM`. As notificações usam SMTP em `MAIL_HOST` e `MAIL_PORT` (padrão `localhost:1025`).

```bash
bash backend/mvnw -f backend/pom.xml -Pintegration clean package -DskipTests
java -jar backend/target/pessoa2-backend-0.0.1-SNAPSHOT-integration.jar
```

O Flyway executa P1 em `public`, P2 em `pessoa2` e P3 em `deliveries`, com um histórico por conjunto e versões originais preservadas. As tabelas de negócio do núcleo e das entregas ficam nos schemas definidos pelos módulos. Para partir de dados P1/P3 existentes, use uma cópia do banco com os históricos correspondentes. Dados do workspace P2 isolado ficam no seu banco de origem e exigem importação explícita para o schema `pessoa2`.

### Frontend e smoke test

Para a demonstração, inicie o JAR com `SPRING_PROFILES_ACTIVE=integration-demo`. Isso cria a origem, a loja e a frota fictícias de forma idempotente. Em um banco de demonstração, escolha uma senha descartável em `P2_SMOKE_PASSWORD` e execute:

```bash
psql -v ON_ERROR_STOP=1 -f scripts/seed-integration-actors.sql
python3 scripts/smoke-integration.py
```

Configure o cliente `psql` para o mesmo banco do backend. O seed adiciona um operador e uma conta de outra loja. O teste cria pedidos e rotas com reservas persistentes, então use um banco descartável novo a cada execução. `P2_SMOKE_URL` aponta para o backend se ele usar uma porta diferente de 8080.

Abra o painel da Pessoa 2 com `npm ci && npm run dev -- --mode integration`, dentro de `frontend`. Faça login com o operador de demonstração (`sprint4-operator@example.invalid`) para consultar e atribuir uma rota pelo UUID. `P2_API_PROXY` ajusta o endpoint de desenvolvimento. Em produção o painel pode ser servido sob um caminho do mesmo domínio com `P2_FRONTEND_BASE`, mantendo `/api/v1` no backend integrado.

## Demonstração isolada das Sprints 1–3

Este workspace também pode demonstrar parceiros e frota sem iniciar os serviços dos outros workspaces. Os dados e IDs abaixo são fictícios e locais.

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

Os testes de contrato usam fixtures JSON v1 e cobrem consulta de parceiro, serialização de atribuição, pré-condições de rota, capacidade, idempotência, conflito e concorrência.

### Evidências — Sprint 4

| Execução | Resultado |
|---|---|
| Local — suíte completa backend + PostgreSQL | `.\mvnw.cmd --batch-mode -DfailIfNoTests=true test` — **92 testes passaram** (0 falhas, 0 erros, 0 ignorados), incluindo `FleetAssignmentIntegrationTests` (15), `FleetContractJsonTests` (3), `FakeRouteSnapshotAdapterTests` (2) e demais. |
| Local — build integrado (`-Pintegration`) | `.\mvnw.cmd --batch-mode -Pintegration package -DskipTests` — `BUILD SUCCESS`; JAR `pessoa2-backend-0.0.1-SNAPSHOT-integration.jar` gerado. |
| Local — smoke test integrado | `python scripts/smoke-integration.py` contra `IntegratedApplication` em porta 18080 com banco `sprint4_smoke_db` descartável — **Smoke OK.** Rota própria persistida; concorrência sem reserva dupla. |
| Local — Flyway reinicialização | Segunda inicialização do mesmo JAR contra o mesmo banco — validação limpa do histórico, sem re-aplicação de migrações. |
| Local — frontend | `npm ci && npm test && npm run build` — **16 arquivos / 51 testes passaram**; build aprovado. |
| CI — workflow `backend-ci.yml` | Compila o perfil `integration` e executa os smokes de P2/P3 com bancos descartáveis separados no PostgreSQL 16. Também compila o frontend P2 após o smoke. |


Após atualizar a branch para a `develop` de `18acff2`, o JAR integrado e os dois smokes passaram em PostgreSQL 16 descartável. O smoke do P2 verificou origem e frota reais, autorização por JWT, uma única reserva na disputa por um par e rastreamento público de código existente (200) e desconhecido (404). A rota em `IN_PROGRESS` mantém a entrega em `ASSIGNED` até o comando de trânsito da entrega. O smoke do P3 verificou a autorização dos motoristas, a entrega concluída, os PDFs acadêmicos e cinco mensagens recebidas no Mailpit. O teste de frete do P3 usa a resposta OSRM controlada pelo smoke.
