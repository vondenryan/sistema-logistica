# Sprint 4 — preparação parcial e evidências — 16/11/2026

**Estado: preparação parcial concluída; integração real e aceite da Sprint 4 bloqueados.** O usuário confirmou que as Pessoas 2 e 3 ainda não fizeram entregas. A develop contém apenas `workspaces/pessoa-1`, sem branches remotas P2/P3 no inventário inicial. Nenhum módulo alheio foi reimplementado e nenhum adaptador fake foi acrescentado para satisfazer o perfil integrado.

A branch `feat/p1-sprint-4` foi criada de `origin/develop` em `16dd845`, após merge humano do PR #5, incluindo a correção de retorno à lista de pedidos. Três subagentes `gpt-6.1-terra`, raciocínio `medium`, trabalharam em backend/perfis/imagem Java, frontend/Nginx e Compose/inventário/documentação. O coordenador revisou e validou os componentes.

## Verificações executadas

| Verificação | Resultado e limite |
| --- | --- |
| `cd backend && ./mvnw -o -B package` | 25 testes, sem falhas/erros/skips; JAR executável gerado |
| Frontend: testes e build npm em modo offline | 33 testes e build TypeScript/Vite aprovados |
| `python3 -m unittest discover -s contract-tests -v` | 16 testes aprovados, incluindo o inventário |
| `docker compose -f compose.integration.yml config -q` | Configuração válida com valores temporários sem imprimir credenciais; não inicia nem valida integração |
| Inventário real `scripts/check-integration.py` | Retorno 1 esperado: fontes, migrações e telas P2/P3 ausentes. Não é um teste de integração aprovado |
| Manifestos e pulls das imagens | Java 21 JRE, nginx:1.28-alpine e axllent/mailpit:v1.27.8 disponíveis; python:3.12-slim usado somente no cliente de teste |
| Builds Docker backend/frontend | Aprovados com rede de build desabilitada e artefatos compilados no host |
| Backend em integration, sem provedores | Inicialização recusada antes do banco/credenciais, com as sete portas ausentes listadas |
| Backend em dev+integration | Inicialização recusada pelo bloqueio de perfis |
| Backend em integration com roteamento fake | Inicialização recusada; sem fallback para fake |
| Container backend P1 no teste de componentes | Usuário 10001:10001, Java executando o fluxo P1 com perfil dev/fakes explicitamente selecionado apenas no teste |
| Nginx com backend real P1 no teste | HTML e assets, fallback SPA, 404 de asset ausente, `/api/v1`, query string, autenticação, 401 e cache no-store verificados |
| Fluxo HTTP P1 através do gateway | Dois pedidos → confirmação repetida → frete → sugestão → reordenação → atribuição repetida → início; aprovado com PostgreSQL real e integrações fake P1 |
| Mailpit | Capturou mensagem SMTP sintética local; nenhuma notificação da aplicação/P3 foi validada |
| `python3 scripts/verify-sprint4-components.py` | Passou em rede Docker interna sem acesso externo; limpeza preservou containers, volumes e redes preexistentes |
| `git diff --check` | Aprovado |

Foram diagnosticadas duas condições do ambiente de teste: redes Docker internas não publicam portas neste ambiente, e o Docker injeta proxy em clientes. O cliente passou a executar dentro da rede, com bypass do proxy apenas para aliases internos. Isso não alterou a política de rede do ambiente.

Foi corrigido um defeito real da imagem frontend: builds feitos com umask 077 geravam index.html 600 e pasta assets 700; o worker Nginx não conseguia lê-los. O Dockerfile agora normaliza os diretórios públicos para 755 e arquivos para 644. O teste do frontend servido, que falhou antes, passou após reconstruir a imagem.

O teste de componentes e a mensagem sintética não equivalem ao E2E da Sprint 4. O Compose permanece em `integration`/OSRM, sem DEMO_PASSWORD ou perfil dev/test. A propriedade de senha demo foi separada para application-dev.yml. O guard inventaria presença de beans necessários, sem presumir que presença comprove implementação real ou compatível. O frontend `/healthz` verifica apenas o próprio frontend.

## Trabalho ainda bloqueado

Receber partners/fleet (P2) e deliveries/notifications/documents (P3), suas telas, contratos e migrações. Depois: compor o monólito real e shell frontend, escolher a versão comum de build com os projetos entregues, ligar as sete portas externas e JWT/snapshots, coordenar Flyway sem alterar V1–V6, provisionar usuários reais de demonstração no perfil integrado e executar testes de consumidor/provedor, segurança, falhas e E2E sem fakes até tracking, e-mail e PDF acadêmico.

Continuam pendentes o valor operacional da tarifa e geocodificação; não foi presumido valor de implantação. O ciclo terminal e o comando de transição da entrega no início da rota seguem as limitações do v1. A conexão Supabase não foi testada ou modificada; os arquivos locais pendentes desse trabalho ficam fora desta entrega.

Não houve composição com P2/P3, validação de migrações globais, envio real de e-mail, PDF da aplicação, publicação do ambiente ou merge pelo agente. A criação do PR será tentada como rascunho de preparação parcial; integração não será marcada como concluída.
