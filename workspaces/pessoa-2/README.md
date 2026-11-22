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

## Cadastro de origens

O backend expõe `GET /api/v1/origins?page=0&size=20`, `GET /api/v1/origins/{originId}`, `POST /api/v1/origins` e `PATCH /api/v1/origins/{originId}`. A coleção segue a paginação de lojas. A origem retorna `{id,type,storeId,address,active}`; `type` é `STORE` ou `DISTRIBUTION_CENTER`, e ambos exigem um `storeId` já existente. O endereço segue `AddressSnapshot` em `docs/CONTRACTS_V1.md`. O PATCH aceita `type`, `storeId`, `address` (objeto completo) e/ou `active`; use `{"active":false}` para inativar, sem exclusão física.

Somente `ADMIN` e `OPERATOR` podem listar, criar ou atualizar origens. `STORE` pode consultar somente origens vinculadas à sua loja; tentativa de acesso a outra origem retorna 404. `DRIVER` recebe 403. Requests desconhecidos são rejeitados, e erros usam RFC 9457 `ProblemDetail`.

**Ponto em aberto:** o contrato não define se uma origem pode estar vinculada a uma loja inativa. Essa regra não é aplicada pelo cadastro e requer decisão de negócio posterior.

As credenciais do `.env.example` são somente para desenvolvimento local; não use `.env` em ambientes compartilhados ou de produção.
