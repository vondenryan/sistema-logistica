# Sprint 4 — operações parciais

P2/P3 ainda não entregaram fontes. A Sprint 4 não está concluída e a configuração proposta não é uma integração funcional. `compose.integration.yml` é uma topologia preparatória com projeto `p1-integration` e volume próprio, separado do Compose local existente. Os Dockerfiles P1 usam artefatos gerados previamente: JAR em `backend/target/` e build em `frontend/dist/`; siga os respectivos READMEs para construí-los. O frontend serve `/healthz` com HTTP 200 e encaminha `/api/` ao backend. O healthcheck verifica somente o frontend; `service_started` não prova disponibilidade ou integração do backend. O perfil `integration` não ativa fixtures dev/test e faltam adaptadores reais; o backend falha deliberadamente ao iniciar enquanto essas portas estiverem ausentes.

Para inspecionar o inventário, a partir da raiz do repositório:

```sh
python3 workspaces/pessoa-1/scripts/check-integration.py
python3 -m unittest discover -s workspaces/pessoa-1/contract-tests -p test_integration_preflight.py
```

O script somente verifica arquivos, sem executar módulos: para cada workspace `pessoa-2`/`pessoa-3`, procura fontes Java/Kotlin sob `backend/src/main/java/**/<modulo>/`, SQL sob `backend/src/main/resources/db/migration/<modulo>/` e fontes Vue/JS/TS sob `frontend/src/`. Espera `partners`, `fleet`, `deliveries`, `notifications` e `documents` nos respectivos donos. Diretórios vazios, README e `.gitkeep` não contam. Caminhos reais podem diferir e exigirão revisão explícita; arquivos com essas extensões não comprovam implementação real. Com P2/P3 ausentes, o retorno 1 é esperado e não representa um teste de integração aprovado; retorno 0 significa somente inventário presente. Contratos, build, adaptadores reais e E2E continuam obrigatórios em ambos os casos.

Para uma futura execução local, copie `integration.env.example` para `.env.integration.local` (já ignorado pelo Git) ou para um arquivo privado fora do checkout e forneça `DB_PASSWORD`, `JWT_SECRET` (pelo menos 32 bytes) e `OSRM_BASE_URL`. O URL deve ser alcançável **de dentro do container**; `localhost` ali não representa a máquina host. Não há OSRM incluído. Não inclua `DEMO_PASSWORD`. Valide sem imprimir a configuração expandida, por exemplo, a partir de `workspaces/pessoa-1`:

```sh
docker compose --env-file .env.integration.local -f compose.integration.yml config -q
```

Após existirem imagens e adaptadores reais, o mesmo `--env-file` e `-f` podem ser usados com `up --build`. Frontend, backend e interface Mailpit ficam em `127.0.0.1:18000`, `127.0.0.1:18080` e `127.0.0.1:18025`; PostgreSQL não publica porta. Mailpit recebe SMTP interno em `mailpit:1025`; `SMTP_HOST` e `SMTP_PORT` são intenção de configuração para P3, ainda não consumidos pelo código atual. Não apagar o volume para resolver falhas: ele pode conter histórico/dados.

Antes de executar migrações integradas, inventariar históricos e conflitos de versões; preservar V1–V6 já aplicadas, sem alterá-las ou repetir versões globais. Definir coordenação e validar banco limpo e banco com histórico, preservando dados. A presença dos serviços ou um preflight verde não comprova essa validação, nem substitui testes de contrato, segurança, falhas, recuperação e E2E com adaptadores reais.

Um teste isolado P1 com perfil dev e fakes ou uma mensagem sintética capturada no Mailpit verifica componentes isolados; nenhum deles comprova integração sem fakes ou entrega de P2/P3.

A tarifa `FREIGHT_TARIFF_PER_KM` pode ser fornecida no arquivo privado depois da decisão operacional; vazia não impede configuração do Compose, mas novas cotações serão rejeitadas. Não há tarifa presumida. Em ambientes que injetam proxy nos containers, preserve o proxy para destinos externos e configure NO_PROXY para os nomes de serviços internos quando o cliente HTTP precisar acessá-los diretamente.

## Teste reproduzível dos componentes P1 (não é integração P2/P3)

As imagens de teste são construídas com os artefatos já gerados no host:

```sh
# A partir de workspaces/pessoa-1, após Maven package e npm run build:
docker build --network=none -t sistema-logistica-p1-backend:sprint4-check backend
docker build --network=none -t sistema-logistica-p1-frontend:sprint4-check frontend
python3 scripts/verify-sprint4-components.py
```

O teste exige Docker e as imagens já em cache: Java/Nginx usadas pelos Dockerfiles, `postgres:16`, `axllent/mailpit:v1.27.8` e `python:3.12-slim` (somente cliente de teste, não serviço da aplicação). Não faz downloads. Cria sua própria rede interna sem egress, containers e PostgreSQL descartável; gera credenciais temporárias em memória, usa **dev/fakes e tarifa 2.50 apenas de teste**, verifica gateway/SPA/autenticação/fluxo P1 e captura uma mensagem SMTP sintética. O cliente usa NO_PROXY apenas para os nomes internos do teste, sem mudar o proxy do ambiente. Remove seus próprios containers, volumes anônimos e rede ao terminar. Não toca no Compose de desenvolvimento nem no banco Supabase. O resultado não prova notificações da aplicação nem entrega de módulos P2/P3.
