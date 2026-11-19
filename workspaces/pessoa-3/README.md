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

2. Suba o banco de dados PostgreSQL local isolado:
   ```bash
   docker compose up -d
   ```
   O PostgreSQL será iniciado na porta **5434**, com banco `logistics_p3` e credenciais `logistics`/`logistics`.

---

## 4. Executando a Aplicação

Para iniciar o backend Spring Boot:
```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
A API estará acessível em `http://localhost:8083`.

---

## 5. Executando os Testes

Os testes são totalmente autocontidos e executam sem necessidade de serviços externos ou Docker ativos (utilizando banco H2 em modo de compatibilidade PostgreSQL com Flyway):

```bash
cd backend
mvn test
```

---

## 6. Endpoints REST da Sprint 1

| Método | Endpoint | Descrição | Regra de Autorização |
|---|---|---|---|
| `GET` | `/api/v1/deliveries/my` | Lista entregas do motorista | DRIVER atribuído |
| `GET` | `/api/v1/deliveries/{id}` | Consulta detalhes da entrega | DRIVER atribuído, OPERATOR ou ADMIN |
| `GET` | `/api/v1/deliveries/{id}/events` | Lista histórico imutável de eventos | DRIVER atribuído, OPERATOR ou ADMIN |
| `PATCH` | `/api/v1/deliveries/{id}/status` | Atualiza status (`IN_TRANSIT`, `FAILED_ATTEMPT`, `ASSIGNED`) | DRIVER atribuído ou OPERATOR |
| `POST` | `/api/v1/deliveries/{id}/complete` | Conclui entrega com nome do recebedor | DRIVER atribuído ou OPERATOR |
