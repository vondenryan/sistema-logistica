# Evidências — Sprint 3 — Pessoa 1 — 16/11/2026

Branch `feat/p1-sprint-3`, criada de `origin/develop` em `e510adc`, após o merge humano do PR #4. A correção de cotação por pedido foi preservada. Três subagentes `gpt-6.1-terra`, raciocínio `medium`, receberam caminhos exclusivos e testes independentes: backend, frontend e operações/contratos. O coordenador revisou o código e executou as verificações de composição.

## Verificações executadas

| Comando/verificação | Resultado |
| --- | --- |
| `cd backend && ./mvnw -o -B test` | 20 testes, 0 falhas/erros/skips, BUILD SUCCESS |
| `cd frontend && npm --offline --update-notifier=false test` | 29 testes aprovados |
| `cd frontend && npm --offline --update-notifier=false run build` | TypeScript e build Vite aprovados |
| `python3 -m unittest discover -s contract-tests -v` | 13 testes aprovados |
| `./scripts/verify-sprint3.sh` | Duas execuções aprovadas, a segunda após as alterações finais |
| PostgreSQL descartável real | Duas confirmações, duas cotações, uma rota iniciada, duas paradas; verificações SQL aprovadas |
| Smoke HTTP no backend real com fakes | Login, autenticação ausente/inválida, autorização DRIVER, validação/ProblemDetail, pedido→frete→rota, reordenação, atribuição repetida e início aprovados |
| Cleanup após ambas as execuções | Mesmos containers e volumes existentes antes do teste; nenhum recurso descartável residual |
| Chromium real com HTTP local simulado | Botão de entrada disponível imediatamente após logout com pedido pendente; resposta antiga não aparece no novo login; falha 503 da lista inicial é exibida |
| `git diff --check` e sintaxe shell | Aprovados |

O build do verificador usa Maven `-o`, cópia privada de fontes/cache e imagem PostgreSQL em cache com `--pull never`. A aplicação recebe ambiente filtrado, senha/JWT temporários, provider fake e tarifa **2.50 exclusivamente de teste**. Não há chamadas a Supabase, OSRM público, geocodificação ou servidores P2/P3. Isso demonstra independência desses serviços; não foi aplicado bloqueio de rede em nível de pacote. Requisitos e detalhes de limpeza estão em [operações](SPRINT_3_OPERATIONS.md).

## Falhas reproduzidas e correções

- Fakes ignoravam indisponibilidade em algumas operações ou lançavam erros não canônicos em entradas nulas. Validadores e cenários negativos foram alinhados.
- O fake de frota segurava o monitor durante leitura de snapshots. A leitura ocorre antes da seção crítica; a reserva em memória continua atômica. A liberação exige rota concluída, sem criar endpoint de ciclo terminal.
- URL desconhecida autenticada era traduzida como erro interno; agora retorna HTTP 404 com NOT_FOUND. Parâmetro obrigatório ausente é tratado como validação.
- Falhas do reconciliador eram silenciadas; agora são registradas somente com ID e código seguro, mantendo o retry pendente.
- Billing dependia de uma interface em orders.application; a porta interna de pagamento simulado foi movida para logistics.shared. Os contratos públicos congelados não foram alterados.
- Respostas atrasadas de sessão/pedido podiam restaurar estado após logout/troca de conta. O contexto invalida respostas antigas, limpa o formulário e não mantém o botão de entrada bloqueado.
- A falha da lista após login podia ser ignorada, e uma resposta de componente desmontado podia expirar uma sessão nova. Os fluxos têm testes específicos e foram corrigidos.
- Distância sugerida permanecia após mudança da seleção; consulta de rota com erro deixava a rota anterior visível. Ambos os estados são invalidados.

Os testes novos de recuperação usam transação SQL H2 real com falha injetada: uma falha de entrega após reserva mantém a rota atribuída e bloqueia início até retry; uma falha SQL após atualizar a atribuição faz rollback para PLANNED e permite repetir a mesma reserva. Verificam também pagamento único, leitura pelo motorista atribuído e 404 para outro motorista com JWT válido. O smoke com PostgreSQL valida a composição real, sem representar essas injeções como falhas reais do serviço PostgreSQL.

## Limites mantidos

Integração com os provedores reais P2/P3 continua na Sprint 4. O inventário está em [INTEGRATION.md](../INTEGRATION.md). SQL e reserva externa não são uma transação distribuída: após rollback SQL, a reserva fake pode permanecer em memória até repetir a mesma rota/reiniciar; o teste e o handoff documentam essa recuperação.

A unicidade histórica das paradas e a ausência de comando v1 para levar entregas a IN_TRANSIT no início da rota permanecem limitações do ciclo terminal/integrado. Não foram inventadas regras de cancelamento ou funcionalidades dos outros integrantes. Tarifa de implantação, geocodificação e DAT pendentes continuam explícitos. Não houve migração nova nem alteração de V1–V6.

A configuração Supabase pendente foi preservada fora desta entrega. Não houve publicação do ambiente, merge pelo agente, alteração de CI global ou dos contratos compartilhados. O resultado do envio Git/abertura de PR será registrado separadamente.

## Entrega Git

O commit de implementação `adaab49` foi enviado para `origin/feat/p1-sprint-3`. A abertura de PR com `gh pr create --base develop --head feat/p1-sprint-3 --body-file workspaces/pessoa-1/docs/SPRINT_3_PR.md` foi impedida por `Post https://api.github.com/graphql: Forbidden`; nenhum PR foi criado automaticamente. Abrir manualmente: https://github.com/vondenryan/sistema-logistica/compare/develop...feat/p1-sprint-3?expand=1 . Base develop, comparação feat/p1-sprint-3. Sem merge ou publicação pelo agente.
