# Aplicação integrada

A composição reaproveita o perfil Maven `integration` do backend P2. Um único JAR reúne a identidade JWT, pedidos, frete e rotas (P1), parceiros e frota (P2), e entregas, rastreamento, notificações e documentos (P3). A ligação usa as portas Java v1. O perfil integrado usa os adaptadores reais e tem dois resolvedores de IP com nomes distintos para login e rastreamento. O gateway substitui os cabeçalhos de IP e o backend confia apenas no IP exato do gateway.

## Base da integração

A branch parte de `develop` em `076a5ae`.

| Pessoa | Sprints já presentes na base, em ordem | PR pendente na base |
| --- | --- | --- |
| P1 | Sprint 1 via `57767b3` (#2), Sprint 2 via `e510adc` (#4), Sprint 3 via `16dd845` (#5) | Sprint 4 (#7), ponta `ed0e6ab`. Esta integração reaproveita o limite de login atrás do proxy. |
| P2 | `21ae327` (#11), `3c17e53` (#13), `a9972fe` (#17), `076a5ae` (#16) | — |
| P3 | `7fc19f5` (#9), `36520ad` (#10), `9274902` (#12), `18acff2` (#15) | — |

## Subir

Requisitos: Java 21, Maven 3.9, Node 22, npm e Docker Compose v2. Na raiz, rode:

```sh
bash integration/build.sh
cp integration/.env.example integration/.env
```

Preencha o arquivo privado com uma senha para o PostgreSQL, um segredo JWT independente (ao menos 32 bytes), a URL do OSRM acessível ao backend e a tarifa aprovada para esse ambiente. Para OSRM em rede privada atrás de uma configuração de proxy, acrescente o hostname a `OSRM_NO_PROXY`. Se a sub-rede Docker padrão colidir com outra rede, ajuste `INTEGRATION_SUBNET`, `INTEGRATION_DYNAMIC_RANGE` e `GATEWAY_IPV4` juntos. O IP fixo deve estar na sub-rede e fora da faixa dinâmica.

```sh
cd integration
docker compose up -d --build --wait
curl -fsS http://127.0.0.1:18000/ready
```

O gateway abre em **http://127.0.0.1:18000/**. Use os links para pedidos, frota e entregas. As três interfaces enviam requisições ao mesmo `/api/v1`. Cada módulo mantém a sessão JWT da própria interface durante a recarga da aba. O rastreamento é acessível pelo link público sem autenticação. O Mailpit abre em **http://127.0.0.1:18025/** e recebe SMTP internamente em `mailpit:1025`. O PostgreSQL e o servidor Java ficam na rede do Compose. A prontidão exige que o Spring conclua a inicialização, incluindo o Flyway, e consiga consultar o banco.

Para dados de demonstração, defina `INTEGRATION_PROFILE=integration-demo` no arquivo privado antes de subir. Esse perfil cria parceiros e frota fictícios e usa as mesmas portas e a mesma identidade JWT da integração. Em um banco descartável, exporte uma senha de demonstração e faça o seed das contas:

```sh
export P2_SMOKE_PASSWORD="$(openssl rand -hex 16)"
docker compose exec -T -e P2_SMOKE_PASSWORD postgres psql -U logistics -d logistics \
  < ../workspaces/pessoa-2/scripts/seed-integration-actors.sql
docker compose exec -T -e P2_SMOKE_PASSWORD postgres psql -U logistics -d logistics \
  < ../workspaces/pessoa-3/scripts/seed-smoke-drivers.sql
```

Use `sprint4-operator@example.invalid` para administrar e `sprint4-driver@example.invalid` para o motorista predefinido. Ao iniciar uma rota, o motorista registra a entrada em trânsito no painel. Quando todas as entregas estiverem concluídas, um operador pode concluir a rota pelo painel P1 ou com `POST /api/v1/routes/{id}/complete` e corpo `{}`. O comando verifica as entregas pela porta do P3 e libera motorista e veículo pela porta do P2 na mesma transação. Repetir o comando devolve a rota concluída.

## Validar

Na raiz, após o build, rode `bash integration/verify.sh` com Python 3, OpenSSL, curl e `pdftotext` instalados. O script usa um projeto Compose descartável, portas locais 18000 e 18025 e uma resposta OSRM determinística. Use `TEST_GATEWAY_PORT` e `TEST_MAILPIT_PORT` para trocar essas portas. O smoke cadastra os recursos, testa autorização e rastreamento, confirma a entrega, confere notificações e PDFs e faz duas rotas disputarem o par liberado. Em seguida restaura uma cópia do banco com dados e histórico Flyway e compara os checksums e as reservas após reiniciar. Ele limpa os recursos descartáveis ao terminar.

Para validar também a navegação no Chromium, instale Playwright para Python e seu navegador, depois execute `RUN_BROWSER_SMOKE=1 bash integration/verify.sh`. Com Chromium instalado no sistema, use `CHROMIUM_PATH` com o caminho do executável.

A CI executa as suítes isoladas dos três workspaces e os contratos, compila os três frontends e executa a jornada pelo Compose. As migrações versionadas já existentes continuam nos conjuntos de origem, com históricos independentes para o núcleo, P2 e P3. A restauração automatizada cobre cópias de um banco integrado. Para trazer dados de um workspace isolado, siga as regras de importação do módulo, especialmente para tabelas da Pessoa 2 que precisam estar no schema `pessoa2`.
