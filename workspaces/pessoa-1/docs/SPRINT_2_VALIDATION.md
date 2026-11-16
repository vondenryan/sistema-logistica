# Evidências — Sprint 2 — Pessoa 1

Validação realizada em 15/11/2026 (America/Sao_Paulo; logs UTC já em 16/11).

Branch `feat/p1-sprint-2` criada de `origin/develop` no commit `57767b3`, após os merges humanos dos PRs #1 e #2. Três subagentes `gpt-6.1-terra`, raciocínio `medium`, trabalharam em conjuntos exclusivos: backend; frontend; operações/contratos. Cada frente validou seu código sem depender das demais. A composição e a revisão foram executadas pelo coordenador.

## Resultados efetivamente executados

| Verificação | Resultado |
| --- | --- |
| `./scripts/test-backend.sh` | 14 testes; 0 falhas, erros ou skips; BUILD SUCCESS |
| `./backend/mvnw -f backend/pom.xml -DskipTests package` | JAR executável gerado; testes executados separadamente acima |
| `./scripts/test-frontend.sh` | npm ci, 14 testes e build TypeScript/Vite passaram |
| `python3 -m unittest discover -s contract-tests -v` | 10 testes passaram, incluindo respostas inválidas no stub do smoke |
| `python3 scripts/smoke-sprint2.py` | Backend real + PostgreSQL + provider fake: dois pedidos, confirmação repetida, frete, sugestão, reordenação, atribuição repetida e início passaram |
| Mesmo smoke na instância secundária | Backend real + PostgreSQL limpo + adaptador OSRM real conectado a servidor HTTP local de teste: passou |
| Verificações HTTP adicionais | Escopo entre lojas/DRIVER, 0/11/repetição de paradas, loja/origem incompatíveis, rascunho e início/reordenação inválidos rejeitados |
| Duas cotações HTTP simultâneas | Mesmo quoteId e snapshot; apenas uma cotação persistida |
| Duas criações HTTP simultâneas com mesmo pedido | Uma criação HTTP 201; outra HTTP 409 |
| Atualização PostgreSQL com histórico | V5–V6 aplicadas sobre V1–V4; oito pedidos preexistentes preservados |
| Banco separado inicialmente vazio | V1–V6 aplicadas com sucesso |
| Reinício com JAR final | Migrações validadas sem reaplicação; reservas e entregas fake restauradas |
| Mudança da tarifa de teste 2.50→7.50 | Cotação histórica idêntica após reinício |
| Tarifa ausente | Nova cotação HTTP 422; snapshot histórico ainda consultável |
| OSRM indisponível no servidor de teste | Nova cotação e sugestão HTTP 503; frete não inventado; snapshot anterior permanece disponível |
| Conversão/arredondamento com OSRM HTTP local | 12345 metros → 12.345 km; tarifa de teste 2.50 → BRL 30.86 |
| Chromium real com frontend/backend/PostgreSQL | Login, novo pedido, clique no mapa, entrada manual com tiles bloqueados, confirmação, frete, sugestão e persistência de rota passaram |

As suítes backend usam H2/Mockito e servidor OSRM HTTP local. As suítes frontend usam HTTP/Leaflet fake. O smoke isolado do cliente também tem servidor stub próprio; seu resultado não foi confundido com os testes usando backend/PostgreSQL reais. Os testes ao vivo foram executados com JAR copiado, para evitar interferência de recompilações paralelas em target/classes.

Screenshots do navegador: [cotação](SPRINT_2_FREIGHT.png) e [rota](SPRINT_2_ROUTE.png). Dados e preços são exclusivamente fictícios de teste; nenhuma tarifa de implantação foi definida e nenhum pagamento real foi realizado.

## Limites e pontos para revisão

- O OSRM público foi bloqueado pelo proxy HTTP 403. O adaptador foi validado contra servidor HTTP local; disponibilidade de um provedor externo não foi comprovada. Configure OSRM_BASE_URL e a rede para uso real. Não há fallback fictício em produção.
- Supabase permanece sem conectividade TCP neste ambiente; esta Sprint usa PostgreSQL Docker local. Os arquivos Supabase pendentes de antes da Sprint não foram incluídos.
- A tarifa global de implantação e o provedor de geocodificação continuam pendentes; entrada manual funciona sem geocoder. Cotação usa o snapshot de pedido confirmado, sem cotar rascunhos mutáveis.
- Não há composição com os produtores reais das Pessoas 2/3. Isso pertence à Sprint 4.
- O contrato v1 de entregas não tem comando de início da rota/IN_TRANSIT: iniciar altera a rota; a transição da entrega continua com Pessoa 3, sem alteração silenciosa da porta.
- A unicidade de route_stops impede dupla reserva de pedido; também impede reutilização histórica enquanto o ciclo terminal e a liberação ainda não estão implementados. Esse ciclo não tem endpoint aprovado nesta Sprint e exige revisão futura.
- Não houve alteração dos contratos compartilhados, migrações V1–V4, CI global, merge ou publicação do ambiente.

## Entrega Git

O commit de implementação `295b6f8` foi enviado a `origin/feat/p1-sprint-2`. A tentativa de criar o PR via `gh pr create --base develop --head feat/p1-sprint-2 --body-file workspaces/pessoa-1/docs/SPRINT_2_PR.md` recebeu `Post https://api.github.com/graphql: Forbidden`. Nenhum PR foi criado automaticamente. Abertura manual: https://github.com/vondenryan/sistema-logistica/compare/develop...feat/p1-sprint-2?expand=1 . Manter base develop e compare feat/p1-sprint-2. Não houve merge nem publicação do ambiente.
