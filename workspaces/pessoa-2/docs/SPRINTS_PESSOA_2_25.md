# Sprints independentes — Pessoa 2 — Parceiros e frota (25%)

**Prazo:** 7 dias; **Sprints:** 1 (dias 1–2), 2 (dias 3–4), 3 (dia 5), 4 (dias 6–7).
**Ferramenta de implementação:** Google Antigravity. **Documento de referência:** DAT v1.1; **política:** desenvolvimento isolado até o fim da Sprint 3.


## Contrato de independência — obrigatório para os três

**Objetivo:** cada pessoa consegue iniciar, executar, testar e demonstrar seu módulo **sem código, credenciais, banco, servidor, branch ou aprovação prévia de outro integrante**. Isso não elimina a necessidade de integração final.

### Regras de isolamento
1. Cada integrante cria seu **workspace autônomo no monorepo** (backend Spring Boot + frontend Vue apenas quando necessário), banco PostgreSQL local em Docker e seu próprio `docker-compose.yml`. Não usar Supabase compartilhado durante a implementação isolada.
2. Todos usam Java 21 e fixam **a mesma versão estável de Spring Boot** em seus projetos após uma checagem de compatibilidade. Até essa escolha, o código não deve depender de APIs exclusivas de uma versão específica.
3. Integrações com outros módulos são **portas/interfaces próprias e adaptadores fake**, implementados no mesmo repositório do integrante. Fakes devem ser determinísticos, conter casos de sucesso e erro, e funcionar sem internet.
4. Contratos de comunicação são **JSON versionado em `/api/v1`**, IDs UUID como strings, datas ISO 8601 com fuso, dinheiro em decimal (nunca float), erros no formato `{code,message,details}`. Não compartilhar entidades JPA nem banco entre módulos.
5. Toda dependência de autenticação usa `CurrentActorPort` com `actorId`, `role`, `storeId?`, `driverId?`. Durante desenvolvimento, usar adaptador fake **somente em perfil `dev`**, nunca em produção; testar autorização em casos de uso. Na integração, conectar ao JWT real.
6. Cada módulo mantém migrações Flyway próprias em `db/migration/<modulo>/`; **não** misturar migrações de três pessoas numa pasta única até a integração. Na composição final, reordenar/versionar migrações de modo coordenado.
7. Cada integrante mantém `README.md`, `.env.example`, scripts de execução, seed local idempotente, testes e `INTEGRATION.md` explicando o adaptador real a substituir. Não commitar segredos.
8. Evitar importação de código-fonte entre projetos; compartilhar apenas **esquemas e exemplos JSON neste arquivo**. Alterações em contratos exigem nova versão, sem quebrar o contrato v1.
9. As APIs fake não são solução final: na Sprint 4 serão trocadas por adaptadores reais. **Não afirmar integração concluída antes de executar testes entre módulos.**

### Contratos mínimos congelados (v1)
| Contrato | Entrada | Saída | Dono do serviço real |
|---|---|---|---|
| `PartnerLookupPort` | `storeId` ou `originId` UUID | `{id,type,address,lat,lon,active}`; `type=STORE|DISTRIBUTION_CENTER` | Pessoa 2 |
| `FleetAssignmentPort` | `{routeId,requiredCapacity,originId}` | `{assignmentId,driverId,vehicleId,carrierId?,fleetType}` ou `NO_ELIGIBLE_RESOURCE` | Pessoa 2 |
| `OrderSnapshotPort` | `orderId` UUID | `{orderId,storeId,originId,destination,recipientName,recipientEmail?,trackingCode,freightAmount}` | Pessoa 1 |
| `RouteSnapshotPort` | `routeId` UUID | `{routeId,driverId,vehicleId,orderIds,startedAt?}` | Pessoa 1 |
| `DeliveryStatusPort` | `orderId` UUID | `{orderId,status,updatedAt,receiverName?}` | Pessoa 3 |
| `NotificationRequestPort` | `{eventId,eventType,orderId,to,occurredAt}` | `{accepted,notificationId}`; entrega assíncrona | Pessoa 3 |
| `DocumentRequestPort` | `{orderId,documentType}` | `{documentId,status}` e endpoint para PDF acadêmico | Pessoa 3 |

**Valores comuns:** `role=ADMIN|OPERATOR|STORE|DRIVER`; `documentType=NFE_SIMULATED|CTE_SIMULATED`; status de entrega `PENDING|ASSIGNED|IN_TRANSIT|FAILED_ATTEMPT|DELIVERED|CANCELLED`. Transições e permissões seguem o DAT. IDs de pedidos/rotas/lojas/motoristas são opacos e nunca obtidos por acesso direto às tabelas alheias. O código público de rastreamento é diferente de `orderId`.

### Critério de independência (teste obrigatório)
- [ ] Um clone isolado do projeto inicia com suas próprias configurações.
- [ ] Os testes passam com **todas as integrações externas desligadas**.
- [ ] Um cenário completo do módulo é demonstrável usando adaptadores fake e seed local.
- [ ] Existem testes de contrato com exemplos JSON válidos e inválidos.
- [ ] `INTEGRATION.md` lista precisamente quais fakes serão substituídos e por qual endpoint/porta.

## Sprint 1 — Dias 1–2: cadastros independentes
**Branch obrigatória desta Sprint:** `feat/p2-sprint-1` (única branch de implementação da pessoa nesta Sprint).
- [ ] Criar projeto Spring Boot/Vue próprio com PostgreSQL Docker, Flyway e seed local; **não aguardar estrutura da Pessoa 1**.
- [ ] Implementar `partners`: lojas, centros de distribuição e fornecedores com **cadastro básico**, sem estoque ou portal.
- [ ] Implementar `PartnerLookupPort` real e endpoint `/api/v1/partners/origins/{id}` conforme contrato v1.
- [ ] Criar `CurrentActorPort` fake de desenvolvimento com perfis ADMIN/OPERATOR/STORE; validar escopo nos casos de uso.
- [ ] Criar frontend de cadastro e testes de validação/autorização.
**Aceite:** cadastrar e consultar origens e fornecedores com backend e banco próprios, sem outro módulo.

## Sprint 2 — Dias 3–4: frota e atribuição independentes
**Branch obrigatória desta Sprint:** `feat/p2-sprint-2` (única branch de implementação da pessoa nesta Sprint).
- [ ] Implementar transportadoras, motoristas, veículos, vínculos e distinção frota própria/terceirizada.
- [ ] Implementar disponibilidade, capacidade e regras de elegibilidade.
- [ ] Implementar `FleetAssignmentPort` e endpoint `/api/v1/fleet/assignments` com contrato v1.
- [ ] Usar `RouteSnapshotPort` **fake local** para simular rotas e necessidades de capacidade; não consultar tabelas de `routing`.
- [ ] Priorizar frota própria, aplicar desempate determinístico e impedir reservas simultâneas conflitantes por transação/bloqueio.
- [ ] Implementar telas de frota e testes de concorrência, ausência de elegíveis e atribuição terceirizada.
**Aceite:** atribuir veículo/motorista a uma rota fake, sem backend de pedidos/rotas/identidade.

## Sprint 3 — Dia 5: contrato e demonstração isolada
**Branch obrigatória desta Sprint:** `feat/p2-sprint-3` (única branch de implementação da pessoa nesta Sprint).
- [ ] Testar respostas de `PartnerLookupPort` e `FleetAssignmentPort` com exemplos JSON do contrato.
- [ ] Demonstrar CRUDs e atribuição automática usando seed local e rota fake.
- [ ] Escrever `INTEGRATION.md` e validar execução offline.
**Aceite:** projeto inicia sozinho e testes passam com qualquer serviço externo desligado.

## Sprint 4 — Dias 6–7: conexão com núcleo
**Branch obrigatória desta Sprint:** `feat/p2-sprint-4` (única branch de implementação da pessoa nesta Sprint).
- [ ] Integrar endpoints/portas com `orders` e `routing` da Pessoa 1; trocar fake de rota pelo adaptador real.
- [ ] Conectar `CurrentActorPort` ao contexto JWT real e validar autorização.
- [ ] Consolidar migrações Flyway sem conflito; apoiar seed demonstrativo e smoke tests.
- [ ] Corrigir divergências de contratos sem alterar módulos alheios.
**Aceite:** rota real consegue obter origem e atribuir recursos elegíveis, sem dupla reserva.

## Limites de escopo
**Implementa:** `partners`, `fleet`, atribuição e suas telas. **Não implementa:** autenticação JWT central, pedidos, frete, OSRM, entregas ou PDFs. O fake de autenticação só funciona no perfil `dev`.

## Instrução ao Google Antigravity
> Siga este arquivo como escopo fechado. Crie todos os fakes, seeds, scripts, testes e infraestrutura local necessários para desenvolver sem esperar outro integrante. Preserve os contratos v1. Não modifique código de módulos alheios. Use integrações reais apenas na Sprint 4, após testes de contrato.


---
# Especificação arquitetural executável — versão 2.1

## Fonte de verdade, organização e versionamento
- **DAT v1.1 prevalece** sobre este plano em regras de negócio. Este arquivo detalha implementação e testes; não cria decisões de produto incompatíveis. Questões não confirmadas são marcadas como **PENDENTE**.
- **Um repositório GitHub**, branches `main` (estável), `develop` (integração) e **uma branch por pessoa por Sprint**, `feat/p2-sprint-<n>` criada a partir da `develop` atualizada. Não fazer push direto para `main`/`develop`.
- Para independência inicial, cada pessoa trabalha numa **área autocontida do mesmo monorepo**: `workspaces/pessoa-1/`, `workspaces/pessoa-2/`, `workspaces/pessoa-3/`. Cada workspace pode ter backend, frontend, Compose, banco e fixtures próprios; o código definitivo será incorporado aos módulos `backend/...` e `frontend/src/modules/...` na integração. Isso evita três projetos concorrentes na raiz.
- Cada integrante consegue clonar e executar seu workspace sem depender de artefatos ainda não entregues por colegas. As áreas `contracts/v1/` e `docs/` são compartilhadas, mas os **contratos v1 abaixo são congelados neste arquivo**, permitindo começar sem esperar commits de outros.
- Commits pequenos durante a Sprint e **um PR ao concluir a Sprint**; GitHub Actions executa testes do workspace alterado, validação de contratos e, quando aplicável, build do projeto integrado. **Auto-merge na `develop` somente com checks obrigatórios aprovados e sem conflitos**; revisão humana para contratos, migrações e segurança. Merge automático não implica compatibilidade funcional: rodar E2E na integração.
- Ao integrar, módulos permanecem no monólito Spring Boot; os endpoints HTTP entre workspaces são **contratos de teste**, não obrigação de criar microsserviços em produção. Preferir adaptadores de porta em processo na composição final.
- `main` recebe PR de `develop` somente após smoke test completo. Cada PR descreve mudanças, testes, screenshots de UI e eventuais alterações de contrato. **Não fazer merge direto pelo agente.**

## Protocolo Git obrigatório para Google Antigravity — Pessoa 2

**Regra central:** uma única branch por Sprint por integrante. **Não trocar de branch no meio da Sprint**; fazer quantos commits forem necessários nela. Não reutilizar branch de Sprint anterior. Cada pessoa trabalha somente em `workspaces/pessoa-2/` e arquivos expressamente autorizados. Não alterar `contracts/v1/`, configuração global, CI ou migrações compartilhadas sem revisão humana.

| Sprint | Branch fixa | Momento do PR |
|---|---|---|
| 1 (dias 1–2) | `feat/p2-sprint-1` | Final do dia 2 |
| 2 (dias 3–4) | `feat/p2-sprint-2` | Final do dia 4 |
| 3 (dia 5) | `feat/p2-sprint-3` | Final do dia 5 |
| 4 (dias 6–7) | `feat/p2-sprint-4` | Final do dia 7, após integração/testes |

**Início de cada Sprint** (substituir `N` pelo número da Sprint):

```bash
git status --short                  # confirmar ausência de mudanças não salvas
git fetch origin
git switch develop
git pull --ff-only origin develop
git switch -c feat/p2-sprint-N
```

**Durante a Sprint:** executar testes antes dos commits; commitar por entrega coerente com `feat(modulo): ...`, `fix(modulo): ...`, `test(modulo): ...`, `docs: ...`; nunca commitar `.env`, credenciais ou artefatos gerados. Fazer `git push -u origin feat/p2-sprint-N` após os commits, para backup e visibilidade; **push não é merge**. O agente não deve alterar branch, apagar arquivos alheios, forçar push, fazer rebase destrutivo ou resolver conflito alterando módulos de colegas sem autorização.

**Fim da Sprint:** validar testes e build local, revisar `git diff`, enviar push e **abrir PR `feat/p2-sprint-N` → `develop`** com resumo, testes e riscos. Se a ferramenta não conseguir abrir PR, fornecer URL/etapas para abertura manual. Habilitar auto-merge **somente quando a proteção de branch, CI e revisões exigidas estiverem configuradas**. Falha em check ou conflito bloqueia o merge; solicitar revisão humana. Após o merge confirmado, começar a Sprint seguinte com uma branch **nova a partir de `origin/develop` atualizado**. Não iniciar uma Sprint nova baseada na branch anterior.

**Separação de responsabilidades das ferramentas:** Google Antigravity deve seguir o mesmo protocolo Git e os mesmos contratos de integração. Não pressupor que Codex e Antigravity compartilham contexto, estado de tarefas ou memória. O arquivo `.md` e o DAT são as instruções persistentes; não confiar em histórico de chat de outro agente.

**Integração contínua:** a `develop` recebe código **ao final de cada Sprint**, não a cada commit. `main` recebe PR de `develop` somente após validação final. PRs das três pessoas podem ser aprovados em ordens diferentes; a CI deve validar o estado atualizado da `develop` e os contratos. O fluxo automático precisa ser configurado no GitHub; este documento não o ativa sozinho.

## Padrões compartilhados obrigatórios
- Java 21; Spring Boot estável compatível com Java 21, **versão exata PENDENTE**. Vue 3 + TypeScript + Vite. PostgreSQL local por workspace; Supabase/PostgreSQL como banco previsto para a composição final; Docker Compose local para executar o MVP.
- Hexagonal: `domain` sem Spring/JPA, `application` com casos de uso e portas, `infrastructure` com JPA, REST, SMTP, OSRM, PDF, segurança e configuração. Controller só valida e delega; frontend nunca acessa banco.
- APIs `/api/v1`, JSON, IDs UUID, timestamps UTC em ISO-8601 com offset, valores monetários `BigDecimal`/`NUMERIC(12,2)`; nunca `float` para dinheiro. `ProblemDetail` HTTP com `type`, `title`, `status`, `detail` e extensão `code` estável.
- Identidade `CurrentActorPort {actorId, role, storeId?, driverId?}`. Papéis `ADMIN`, `OPERATOR`, `STORE`, `DRIVER`; autorização por objeto e loja dentro do caso de uso. Fake somente com perfil `dev` e impossível de habilitar por engano em ambiente integrado.
- Integração por portas/DTOs, sem dependência de JPA ou SQL de outro módulo. Banco único no sistema integrado, com schemas/tabelas por módulo e migrações coordenadas. Em desenvolvimento isolado, Flyway em namespace local de cada workspace.
- Não colocar tokens, `.env`, senhas, dados reais ou chaves em Git. `.env.example` sem segredos. Testes offline com fakes determinísticos, incluindo indisponibilidade, erro, duplicidade e concorrência.
- Dados de demonstração fictícios e idempotentes + cadastro manual; nunca dados de compradores reais. `README.md`, `INTEGRATION.md`, fixtures e testes obrigatórios por workspace.

## Contratos v1 congelados para independência
| Porta | Entrada mínima | Resposta mínima | Implementação real |
|---|---|---|---|
| `PartnerLookupPort` | `originId: UUID` | `{id,type,storeId?,address,lat,lon,active}` | Pessoa 2 |
| `FleetAssignmentPort` | `{routeId,requiredCapacityKg,originId}` | `{assignmentId,driverId,vehicleId,carrierId?,fleetType}` ou `NO_ELIGIBLE_RESOURCE` | Pessoa 2 |
| `OrderSnapshotPort` | `orderId: UUID` | `{orderId,storeId,originId,destination,recipientName,recipientEmail?,trackingCode,freightAmount}` | Pessoa 1 |
| `RouteSnapshotPort` | `routeId: UUID` | `{routeId,driverId,vehicleId,orderIds,startedAt?}` | Pessoa 1 |
| `DeliveryStatusPort` | `orderId: UUID` | `{orderId,status,updatedAt,receiverName?}` | Pessoa 3 |
| `NotificationRequestPort` | `{eventId,eventType,orderId,to,occurredAt}` | `{accepted,notificationId}` | Pessoa 3 |
| `DocumentRequestPort` | `{orderId,documentType}` | `{documentId,status}` e PDF de simulação | Pessoa 3 |

**Semântica comum:** `type=STORE|DISTRIBUTION_CENTER`; `fleetType=OWN|THIRD_PARTY`; `documentType=NFE_SIMULATED|CTE_SIMULATED`; `status=PENDING|ASSIGNED|IN_TRANSIT|FAILED_ATTEMPT|DELIVERED|CANCELLED`. `trackingCode` é aleatório e não equivale a UUID. Atribuição deve ser idempotente para o mesmo `routeId`; confirmação de pedido cria no máximo uma entrega e um pagamento simulado por pedido. Eventos externos usam `eventId` para deduplicação.

**Exemplos de fixtures obrigatórios (adaptar UUIDs em testes):**
```json
{"origin":{"id":"11111111-1111-4111-8111-111111111111","type":"STORE","storeId":"22222222-2222-4222-8222-222222222222","address":"Endereço fictício","lat":-26.48,"lon":-49.07,"active":true},"assignmentRequest":{"routeId":"33333333-3333-4333-8333-333333333333","requiredCapacityKg":40,"originId":"11111111-1111-4111-8111-111111111111"}}
```
Coordenadas da fixture são **exemplo técnico**, não endereço de operação definido. Documentar formato e casos de erro em testes de contrato. Mudança incompatível exige `v2`, nunca edição silenciosa de `v1`.

## Portões de qualidade
1. **Isolamento:** workspace sobe com serviços dos outros dois desligados, usando fakes e banco próprio.
2. **Contrato:** payloads válidos e inválidos testados; sem importar entidades de outros módulos.
3. **Segurança:** escopo e autorização testados, sem credenciais no repositório.
4. **Integração:** testes com adaptadores reais e migrações consolidadas na `develop`.
5. **Demonstração:** percurso pedido → frete → pagamento fictício → rota → frota → entrega → rastreamento → e-mail Mailpit → PDF acadêmico.

## Pendências que NÃO podem ser decididas implicitamente pelo Codex
- Provedor de geocodificação compatível com as políticas de uso e persistência de coordenadas; seleção manual no Leaflet e coordenadas pré-validadas precisam funcionar sem provedor.
- Valor numérico inicial da **tarifa global** por km; deve ser configurável e não codificado como decisão definitiva.
- Versão exata estável do Spring Boot; fixar e registrar após validação de dependências.
- Configurações locais, credenciais e conteúdo exato do seed de apresentação.

## Decisões específicas — Pessoa 2 (25%)
### `partners`
- CRUD de lojas, fornecedores **básicos** (identificação, contato, vínculo quando aplicável) e centros de distribuição como origens possíveis. Não implementar estoque, catálogo de fornecedor ou login próprio de fornecedor.
- Endereço textual e coordenadas de origem disponíveis por `PartnerLookupPort`; geocodificação real pertence à integração da Pessoa 1, portanto aceitar coordenadas manuais/fixtures sem esperar API externa.
- Somente perfis autorizados podem criar/editar cadastros. Retornar apenas dados autorizados por escopo. Tabelas de referência `stores`, `suppliers` e estrutura necessária a centros de distribuição, com IDs UUID e timestamps.
### `fleet`
- CRUD `carriers`, `drivers`, `vehicles`; motoristas/veículos terceirizados vinculados a transportadora; próprios sem carrier obrigatório. Veículo inclui placa, capacidade `capacity_kg`, propriedade e estado ativo; motorista inclui vínculo ao usuário de identidade via `userId` opaco.
- Elegibilidade: motorista/veículo ativos, disponíveis, capacidade suficiente para carga estimada, sem alocação ativa incompatível; priorizar frota própria; dentre candidatos equivalentes escolher motorista com menos entregas pendentes e desempatar por UUID/identificador estável. Não fazer seleção aleatória.
- Reservar motorista e veículo de forma **atômica**, com transação e proteção de concorrência; duas chamadas simultâneas não podem reservar o mesmo recurso para rotas incompatíveis. Se nenhum elegível, `NO_ELIGIBLE_RESOURCE`, sem alocação parcial. Idempotência por `routeId`.
- Rota usa um motorista e um veículo; substituição durante transporte fora do MVP. Liberação de reservas por conclusão/cancelamento via contrato/evento, sem acesso direto ao banco de `deliveries`.
### APIs, integração e testes
- Expor cadastros REST protegidos e porta de consulta de origens; `FleetAssignmentPort` consumida pelo roteamento. Para desenvolvimento, usar `RouteSnapshotPort` e `CurrentActorPort` fakes determinísticos.
- Telas Vue para parceiros, fornecedores, transportadoras, motoristas, veículos e visualização da alocação; formulário com validação de capacidade e vínculos.
- Testar capacidade insuficiente, veículo inativo, motorista ocupado, frota própria prioritária, fallback terceirizada, desempate, duas atribuições concorrentes, requisição repetida, nenhuma opção e autorização por perfil.
### Checklist por Sprint
- **S1 (D1–2):** workspace autônomo, banco e Flyway locais, `partners` + CRUD e `PartnerLookupPort`, telas, fixtures e autenticação fake `dev`.
- **S2 (D3–4):** frota própria/terceirizada, algoritmo determinístico, bloqueio concorrente, `FleetAssignmentPort`, telas e testes.
- **S3 (D5):** demonstrar cadastro→rota fake→atribuição sem outro serviço; testes de contrato v1 e documentação `INTEGRATION.md`.
- **S4 (D6–7):** substituir fakes por `CurrentActorPort` JWT e `RouteSnapshotPort` reais, consolidar migrações e validar fluxo com roteamento na `develop`.
