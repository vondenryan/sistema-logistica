# Frontend — Pessoa 2

Aplicação Vue 3 + TypeScript para os cadastros do workspace.

## Desenvolvimento local

1. Inicie o backend com o perfil Spring `dev` na porta `8082`.
2. Na pasta `frontend`, execute `npm install` e `npm run dev`.
3. Abra o endereço informado pelo Vite. As chamadas `/api` são encaminhadas pelo proxy local para `http://localhost:8082`; não é necessário habilitar CORS no backend.

O seletor de perfil e os headers `X-Dev-Role` e `X-Dev-Store-Id` são exclusivos do modo de desenvolvimento local. O seletor suporta `ADMIN`, `OPERATOR` e `STORE`; para `STORE`, informe o UUID da loja. O backend só aceita esses headers no perfil Spring `dev`. Sem headers, mantém a identidade configurada em `application-dev.properties`. Não use esses headers em ambientes integrados ou de produção.

## Validação

- `npm test` executa os testes Vitest.
- `npm run build` verifica os tipos e gera a aplicação de produção.

## Cadastros de frota

O menu de Transportadoras, Motoristas, Veículos e Atribuição de frota, assim como o acesso direto às rotas correspondentes, é restrito aos perfis `ADMIN` e `OPERATOR`. Os três cadastros permitem paginação, criação, edição e inativação sem exclusão física.

A tela de atribuição consulta `GET /api/v1/fleet/assignments/{routeId}` para qualquer UUID. O formulário de demonstração de `POST /api/v1/fleet/assignments` aparece somente no modo de desenvolvimento do frontend e requer que o backend também esteja no perfil Spring `dev`; usa as rotas fixas e a origem do fake local.
