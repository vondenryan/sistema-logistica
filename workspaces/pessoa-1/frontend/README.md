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

## Imagem de runtime do frontend

Gere `dist/` no host antes de construir a imagem; o Dockerfile não instala dependências nem recebe segredos ou argumentos de build:

```sh
npm ci
npm test
npm run build
docker build -t logistica-p1-frontend .
docker run --rm -p 8081:80 logistica-p1-frontend
```

A imagem usa `nginx:1.28-alpine` e serve a SPA em http://localhost:8081. Rotas da SPA retornam `index.html` sem cache persistente; arquivos versionados em `/assets/` podem ser armazenados por um ano. `/healthz` verifica apenas que o Nginx está servindo o frontend, sem verificar backend ou integração.

Em uma futura rede Docker Compose, `/api/` encaminha as requisições para `backend:8080`, preservando caminho, query, método e headers de autenticação. O nome é resolvido pelo DNS interno do Docker em tempo de requisição, permitindo iniciar o frontend antes do backend; enquanto ele estiver indisponível, chamadas à API falharão. Respostas da API não são armazenadas em cache. A execução isolada acima não cria esse backend nem comprova a integração. A validação da configuração no Nginx real e do proxy de rotas, métodos, query e autorização permanece pendente da disponibilidade da imagem e do teste de composição; P2/P3 ainda não entregaram seus módulos ou telas.

O Dockerfile normaliza diretórios públicos para 755 e arquivos para 644, inclusive quando o build no host usa umask 077. Assim o worker Nginx consegue ler o HTML e os assets sem depender das permissões do ambiente de build.
