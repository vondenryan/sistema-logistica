# Workspace — Pessoa 3: Entregas, Notificações e Documentos (25%)

Este workspace contém a implementação autônoma dos módulos de **Entregas (`deliveries`)**, **Notificações (`notifications`)** e **Documentos (`documents`)**, conforme especificado no DAT v1.1 e nos contratos `CONTRACTS_V1.md`.

---

## 1. Visão Geral da Sprint 1

Na **Sprint 1 (Dias 1–2)**, o escopo implementado cobre:
* Módulo `deliveries` com máquina de estados rigorosa do DAT v1.1 (`PENDING → ASSIGNED → IN_TRANSIT → DELIVERED`, retry a partir de `FAILED_ATTEMPT`, cancelamento).
* Histórico imutável de eventos de auditoria na tabela `deliveries.delivery_events`.
* Implementação das portas `DeliveryCommandPort` e `DeliveryStatusPort`.
* Adaptadores fake locais determinísticos: `OrderSnapshotPort`, `RouteSnapshotPort` e `CurrentActorPort`.
* Endpoints REST sob `/api/v1/deliveries` com validação de formato RFC 9457 `ProblemDetail`.
* Migração Flyway local isolada no schema `deliveries`.
* Suíte completa de testes unitários, testes de portas, testes de autorização e teste de aceitação.

---

## 2. Pré-requisitos
* **Java 21** (LTS)
* **Maven 3.9+** (ou Maven do ambiente)
* **Docker / Docker Compose** (para PostgreSQL local isolado em desenvolvimento)

---

## 3. Configuração do Ambiente

1. Copie o arquivo `.env.example` para `.env`:
   ```bash
   cp .env.example .env
   ```

2. Suba o banco de dados PostgreSQL e o servidor SMTP Mailpit locais:
   ```bash
   docker compose up -d
   ```
   * PostgreSQL: porta **5434**, banco `logistics_p3` (`logistics`/`logistics`).
   * Mailpit: SMTP na porta **1025** e interface Web em `http://localhost:8025`.

---

## 4. Executando a Aplicação

Para iniciar o backend Spring Boot, carregue o `.env` a partir deste diretório. As variáveis `NOTIFICATIONS_SMTP_ENABLED=true` e `NOTIFICATIONS_OUTBOX_SCHEDULER_ENABLED=true` do exemplo ativam o SMTP e o envio automático pelo agendador.
```bash
set -a
. ./.env
set +a
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
A API estará acessível em `http://localhost:8083`.

---

## 5. Executando os Testes

Os testes do backend são totalmente autocontidos e executam sem necessidade de serviços externos ou Docker ativos (utilizando banco H2 em modo de compatibilidade PostgreSQL com Flyway):

```bash
cd backend
mvn test
```

Para o frontend Vue 3 (painel do motorista e rastreamento público):

```bash
cd frontend
npm ci
npm test
npm run build
```

---

### Correções da revisão do PR #10

O rastreamento usa o IP da conexão por padrão; proxies devem ser explicitamente autorizados e sobrescrever o cabeçalho encaminhado. O limitador tem capacidade máxima e expiração de clientes inativos. Veja [configuração e limites](INTEGRATION.md#ip-de-rastreamento-e-capacidade-do-limitador).

O painel permite navegar entre páginas, atualizar a página atual e alterar o status de entregas depois dos primeiros 20 itens. O backend rejeita deslocamentos de paginação acima do intervalo suportado.

Validação em 24/11/2026: 68 testes backend e 6 frontend aprovados, além dos builds. No ambiente remoto sem `javac`, o backend foi compilado e testado em Java 21 com Eclipse, usando uma cópia temporária do POM (compiler plugin 3.14.1 e plexus-compiler-eclipse 2.15.0); o POM versionado permaneceu intacto. Os testes de acesso direto e proxy usam MockMvc com o endereço da conexão controlado; não foi validado um proxy de implantação real.

## 6. Endpoints REST (Sprints 1 e 2)

| Método | Endpoint | Descrição | Regra de Autorização |
|---|---|---|---|
| `GET` | `/api/v1/deliveries/my` | Lista paginada de entregas do motorista | DRIVER autenticado |
| `GET` | `/api/v1/deliveries/{id}` | Consulta detalhes da entrega | DRIVER atribuído, OPERATOR ou ADMIN |
| `GET` | `/api/v1/deliveries/{id}/events` | Lista histórico imutável de eventos | DRIVER atribuído, OPERATOR ou ADMIN |
| `PATCH` | `/api/v1/deliveries/{id}/status` | Atualiza status (`IN_TRANSIT`, `FAILED_ATTEMPT`, `ASSIGNED`) | DRIVER atribuído ou OPERATOR |
| `POST` | `/api/v1/deliveries/{id}/complete` | Conclui entrega com nome do recebedor | DRIVER atribuído ou OPERATOR |
| `GET` | `/api/v1/tracking/{code}` | Rastreamento público (`status` e `milestones`) | Público (rate limit por IP) |
| `POST` | `/api/v1/documents` | Gera NF-e/CT-e simulados (`201` novo / `200` existente) | ADMIN, OPERATOR ou STORE própria |
| `GET` | `/api/v1/documents/{documentId}` | Consulta metadados do documento simulado | ADMIN, OPERATOR ou STORE própria |
| `GET` | `/api/v1/documents/{documentId}/pdf` | Retorna PDF acadêmico com marca d'água | ADMIN, OPERATOR ou STORE própria |
