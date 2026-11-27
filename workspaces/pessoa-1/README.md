# Pessoa 1 — núcleo e integração

Workspace isolado de identity, orders, billing, routing e shell Vue. Os contratos normativos estão em [docs/CONTRACTS_V1.md](docs/CONTRACTS_V1.md); em divergência com o planejamento de Sprint, prevalecem aqueles contratos e as decisões aprovadas do DAT. As integrações com outras pessoas usam fakes locais nesta Sprint; veja [INTEGRATION.md](INTEGRATION.md). O banco é local a este workspace, sem dependência dos módulos das outras pessoas.

## Preparação e execução local

Requisitos: Docker Compose, Java 21, Node/npm e Python 3. No diretório `workspaces/pessoa-1`:

```sh
./scripts/generate-env.sh
docker compose up -d
```

O gerador cria `.env` com permissões restritas e segredos aleatórios, sem substituir arquivo existente. Não compartilhe nem versione `.env`; `.env.example` contém apenas nomes. PostgreSQL 16 usa banco e usuário `logistics`, porta local 5433, volume persistente e healthcheck. `DB_PASSWORD` é obrigatório. Remover o volume apaga os dados. Aguarde o healthcheck antes de iniciar o backend.

Em terminais separados, com backend e frontend disponíveis no workspace:

```sh
./scripts/backend.sh
./scripts/frontend.sh
```

O primeiro exporta `.env` e executa `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` em `backend` (porta 8080); o segundo executa `npm run dev -- --host 0.0.0.0` em `frontend` (porta 5173). Instale as dependências do frontend com `npm ci` antes da primeira execução. Os comandos não iniciam automaticamente os outros serviços.

As contas fictícias previstas são `admin@example.invalid`, `store@example.invalid`, `driver@example.invalid` e `operator@example.invalid`, usando `DEMO_PASSWORD` local. Consulte a documentação do backend para seeds efetivamente disponíveis. Não use estas credenciais em produção.

## Verificação

```sh
./scripts/test-backend.sh
./scripts/test-frontend.sh
./scripts/smoke.py
```

Os testes executam respectivamente `./mvnw test` e `npm ci`, `npm test -- --run`, `npm run build`. O smoke exige backend iniciado e cria um pedido; usa login STORE, confirma duas vezes e consulta o pedido. Defaults de fixture: loja `22222222-2222-4222-8222-222222222222`, origem `11111111-1111-4111-8111-111111111111`; ajuste `SMOKE_STORE_ID`, `SMOKE_ORIGIN_ID`, `SMOKE_EMAIL` e `SMOKE_BASE_URL` conforme os seeds reais. Para testar isolamento, informe `SMOKE_OTHER_STORE_EMAIL` e, se diferente, `SMOKE_OTHER_STORE_PASSWORD` para um ator STORE de outra loja. Sem ele, essa verificação é explicitamente omitida. Confirmação repetida verifica resposta idempotente, não prova ausência de duplicatas internas.

## Fluxo Git

Uma branch por pessoa e Sprint; esta Sprint usa `feat/p1-sprint-1`. Ao concluir, abrir PR para `develop`, com testes e riscos, para revisão. Não ativar auto-merge; integração e promoção são decisões posteriores. Opcionalmente, após publicar a branch e revisar o texto em `docs/PR.md`, execute `./scripts/create-pr.sh` para solicitar a criação do PR via `gh`; também é possível informar outro arquivo de corpo como argumento. O script não faz push e requer acesso autorizado ao GitHub. Não alterar módulos de outras pessoas, contratos compartilhados ou CI global sem autorização.

## Decisões pendentes

O DAT aprovado não está disponível aqui; regras de cancelamento tardio não especificadas permanecem bloqueadas. Valor inicial da tarifa global e provedor de geocodificação estão pendentes, enquanto a versão comum do Spring Boot ainda depende de aprovação entre equipes. O backend deste workspace fixa Spring Boot 3.5.7; essa escolha local não representa aprovação para a integração. Não presumir decisões para eles.

Os testes locais de esquema e fixtures de contrato também podem ser executados separadamente:

```sh
python3 -m venv .venv
.venv/bin/pip install -r contract-tests/requirements.txt
.venv/bin/python -m unittest discover -s contract-tests -v
```

Com as dependências já instaladas no ambiente ativo, use `python3 -m unittest discover -s contract-tests -v`. Esses testes não substituem testes contra a implementação real.

## Evidências da implementação

Veja [docs/VALIDATION.md](docs/VALIDATION.md) para testes e limites; [docs/PR.md](docs/PR.md) contém a proposta de PR.

## Sprint 2

Frete e rotas estão descritos em [docs/SPRINT_2_OPERATIONS.md](docs/SPRINT_2_OPERATIONS.md), com [evidências de validação](docs/SPRINT_2_VALIDATION.md). A tarifa exige configuração explícita. O modo offline usa provider fake apenas em desenvolvimento e informa a origem simulada.

## Sprint 3 — estabilização isolada

Execute `./scripts/verify-sprint3.sh` para o smoke automatizado com PostgreSQL descartável e fakes, após instalar os pré-requisitos/cache descritos em [operações](docs/SPRINT_3_OPERATIONS.md). O teste não usa o banco Docker normal nem Supabase e limpa seus próprios recursos. Consulte [evidências e limites](docs/SPRINT_3_VALIDATION.md) e o [inventário de integração](INTEGRATION.md) antes de compor os módulos na Sprint 4.

## Sprint 4 — preparação parcial

As entregas P2/P3 ainda não existem, então a integração permanece bloqueada. Estão preparados Dockerfiles, gateway Nginx, `compose.integration.yml` com PostgreSQL/Mailpit e inventário de pré-requisitos. Veja [operações](docs/SPRINT_4_OPERATIONS.md) e [evidências/pendências](docs/SPRINT_4_VALIDATION.md). O teste de componentes usa dev/fakes explicitamente e não substitui o aceite integrado sem fakes.
