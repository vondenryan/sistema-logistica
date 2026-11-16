## Problema e comportamento

O repositório continha somente um README e não permitia demonstrar o núcleo da Pessoa 1. Esta mudança cria o workspace isolado da Sprint 1 com autenticação JWT, pedidos por loja, frontend Vue e PostgreSQL local, usando adaptadores fake para as dependências das Pessoas 2 e 3.

A branch `feat/p1-sprint-1` parte de `develop`. Backend, frontend e operações foram implementados em áreas de escrita separadas por três subagentes Terra 6.1, com raciocínio médio, sem dependência de entregas entre eles. A composição e a revisão foram feitas posteriormente.

## Escopo

- Java 21 / Spring Boot 3.5.7, migrações locais por módulo, autenticação BCrypt/JWT e contexto do ator.
- Pedidos em rascunho: criação, consulta, edição e cancelamento; confirmação idempotente e isolamento por loja.
- Vue 3 / TypeScript: login e fluxo de pedidos; testes com HTTP fake.
- PostgreSQL 16, scripts de execução e segredos locais gerados fora do Git.
- Cópias dos documentos recebidos e schemas/fixtures locais; sem alteração de `contracts/v1/` compartilhado.

## Validação

As evidências finais estão em `workspaces/pessoa-1/docs/VALIDATION.md`. Não interpretar testes com fakes como integração concluída com as Pessoas 2 e 3.

## Limites e revisão

O DAT v1.1 não foi fornecido. Cancelamento de pedidos confirmados permanece bloqueado, assim como decisões pendentes de tarifa/geocodificação. Frete e roteamento operacional pertencem à Sprint 2; integração real, à Sprint 4. A versão do Spring Boot é uma escolha técnica validada localmente, ainda sujeita a alinhamento com os outros integrantes.

`PATCH /api/v1/orders/{id}` é uma extensão local de edição de rascunho para atender ao CRUD da Sprint 1; precisa de formalização/revisão do contrato antes da integração. Revisar contratos locais, autorização e migrações antes do merge. Não ativar auto-merge.
