# Frontend · Sprints 1 e 2

Vue 3, TypeScript e Vite. Requer Node.js 20.19+ ou 22+ e npm.

```sh
npm ci --cache /workspace/.cache/npm
npm run dev
npm run build
npm test
```

Abra http://localhost:5173. O Vite encaminha `/api` para `http://localhost:8080`; o backend deve estar em execução para uso manual. Os testes usam um fake HTTP local em memória e não precisam do backend, banco ou serviços externos. O JWT fica no `sessionStorage` da aba e é validado em `/auth/me` ao restaurar; sair remove a sessão. As credenciais são fornecidas pelo operador, nunca incorporadas ao frontend. O backend de demonstração usa os e-mails `admin@example.invalid`, `operator@example.invalid`, `store@example.invalid` e `driver@example.invalid`, com senha configurada externamente em `DEMO_PASSWORD`.

Para seleção manual, os IDs ilustrativos do contrato são loja `22222222-2222-4222-8222-222222222222` e origem `11111111-1111-4111-8111-111111111111`. Confirme que estão cadastrados e ativos no backend utilizado. Um ator STORE usa automaticamente o `storeId` da sessão; ADMIN e OPERATOR informam a loja. A autorização definitiva é feita pelo servidor.

A atualização de rascunho é uma extensão local deste workspace para o CRUD da Sprint 1: usa `PATCH /api/v1/orders/{id}` com o `CreateOrderRequest` completo. A operação não está especificada na tabela normativa v1 e precisa ser formalizada no contrato na integração; os contratos compartilhados permanecem inalterados. Confirmação e cancelamento dependem das regras e validações do servidor. Latitude e longitude podem ser omitidas no rascunho, mas são necessárias para confirmação. Valores de peso e dinheiro são mantidos como strings.


A cotação é solicitada em pedidos confirmados e exibe o snapshot retornado pelo servidor, com distância, tarifa, valor e data como strings. Pagamento é somente simulado. A tarifa global precisa estar configurada no backend; indisponibilidade do roteamento ou configuração ausente aparece como erro, sem valor inventado.

ADMIN e OPERATOR planejam rotas selecionando até dez pedidos confirmados da mesma loja e origem, inclusive entre páginas. A sugestão é opcional; sua falha permite continuar manualmente. A ordem pode ser alterada antes da criação ou salva em uma rota ainda não iniciada. A consulta é por ID, pois não existe listagem de rotas no v1; DRIVER consulta apenas sua rota atribuída, sem acesso a pedidos ou comandos. STORE consulta cotação dos próprios pedidos. O servidor aplica a autorização e as demais regras.

O mapa usa Leaflet e blocos OpenStreetMap, sujeitos à conectividade e às políticas do serviço. Um clique preenche as coordenadas; digitação numérica manual funciona sem mapa ou provedor de geocodificação. Não há provedor de geocodificação selecionado. Coordenadas vazias são omitidas em rascunhos e espaços não são aceitos.
