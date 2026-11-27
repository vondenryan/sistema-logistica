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

## Contratos compartilhados

O pacote Java `br.com.sistemalogistica.pessoa2.contracts.v1` no backend é uma cópia local provisória dos contratos compartilhados, incluindo `PartnerLookupPort` e seus snapshots. Ele permanece no workspace até o módulo compartilhado ser congelado; não há uma pasta `contracts/v1/` na raiz do repositório.

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


O adaptador de parceiros usa `contracts.v1.PartnerLookupPort` e converte as coordenadas para `BigDecimal` na interface entre módulos. Os workspaces P1 e P2 mantêm cópias idênticas dos tipos da porta e da verificação de contrato para continuar executando de forma independente. O CI compara essas cópias e executa a verificação tanto no fake do P1 quanto no adaptador PostgreSQL do P2.

O CI provisiona PostgreSQL 16 para os testes do P2 com os valores locais padrão do workspace. A API de origens valida latitude entre −90 e 90 e longitude entre −180 e 180 em POST e PATCH. Os erros com detalhes de campo usam `details[].field` e `details[].reason`.
