# Workspace Pessoa 2

Workspace isolado do módulo de parceiros. O scaffold inicial usa Java 21, Spring Boot 4.2.0, Vue 3, PostgreSQL 16 e Flyway. O Spring Boot 4.2.0 suporta Java 17–27; a versão pode ser revisitada antes da integração.

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

O Flyway aplica `backend/src/main/resources/db/migration` ao iniciar a aplicação. A migração inicial cria apenas o marcador idempotente do workspace; não há cadastros de domínio nesta etapa.

## Build

```powershell
Set-Location backend
.\mvnw.cmd clean verify
Set-Location ..\frontend
npm ci
npm run build
```

Para executar, inicie o backend com `.\mvnw.cmd spring-boot:run` e, em outro terminal, o frontend com `npm run dev`. Para parar o banco sem remover o volume, execute `docker compose down`.

Quando as APIs forem implementadas, os erros HTTP deverão seguir RFC 9457 `ProblemDetail` e as extensões `code`, `details` e `traceId` definidas em `docs/CONTRACTS_V1.md`. Cadastros referenciados deverão ser inativados com `active=false`, sem exclusão física. As APIs e os cadastros ainda não fazem parte deste scaffold.

As credenciais do `.env.example` são somente para desenvolvimento local; não use `.env` em ambientes compartilhados ou de produção.
