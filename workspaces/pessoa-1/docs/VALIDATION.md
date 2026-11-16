# Evidências — Sprint 1 — 2026-11-15

Branch `feat/p1-sprint-1`, criada de `origin/develop` no commit `f11eec531a0941ee11450297f5120aa71220c90f`. Três subagentes `gpt-6.1-terra` com raciocínio `medium` trabalharam com caminhos exclusivos, fixtures e testes próprios. O coordenador revisou e validou o conjunto.

| Verificação executada | Resultado |
| --- | --- |
| `./scripts/test-backend.sh` | 8 testes, 0 falhas/erros/skips, BUILD SUCCESS |
| `./scripts/test-frontend.sh` | npm ci, 6 testes e build TypeScript/Vite passaram |
| `python3 -m unittest discover -s contract-tests -v` | 5 testes passaram |
| `python3 contract-tests/check_live.py` | Passou contra backend real e PostgreSQL 16 |
| `SMOKE_OTHER_STORE_EMAIL=other-store@example.invalid ./scripts/smoke.py` | Login, criação, consulta, confirmação repetida e isolamento entre lojas passaram |
| Mesmo smoke com `SMOKE_BASE_URL=http://localhost:5173/api/v1` | Passou pelo proxy Vite |
| PostgreSQL inicial limpo | Flyway aplicou V1–V4 |
| Reinício via `./scripts/backend.sh` | Flyway validou 4 migrações, nenhuma migração pendente, app iniciou |
| Limite de login real | HTTP 429 e ProblemDetail status 429, Retry-After e X-Request-Id presentes |
| Integridade PostgreSQL após smoke | 6 pedidos confirmados, 6 pagamentos para 6 pedidos distintos, 0 jobs pendentes |
| Downloads Maven e formatador Java | SHA-512 verificado; TLS mantido |

Os testes backend usam H2 em modo PostgreSQL e fakes locais, cobrindo autorização, JWT expirado/forjado, confirmação/reconciliação, reserva concorrente, deduplicação, rate limit e bloqueio de perfis inseguros. Os testes frontend usam HTTP fake. Os testes ao vivo usam backend e PostgreSQL reais deste workspace, com integrações externas fake.

O check ao vivo cobre edição/cancelamento de rascunho, bloqueio de edição/cancelamento confirmado, DRIVER sem acesso, acesso entre lojas, campos desconhecidos, peso numérico JSON e latitude inválida. Os schemas locais cobrem parte dos contratos, sem certificar todas as portas futuras.

## Limites

Não houve integração com implementações das Pessoas 2/3 ou E2E da Sprint 4. Frete, mapas, roteamento operacional e RouteSnapshotPort real ficam na Sprint 2. Os fakes mantêm estado em memória; em dev, a inicialização reabre os jobs de confirmação e reproduz os eventos para restaurar entrega/notificação. Reservas de frota não são restauradas.

O DAT não foi fornecido; tarifa, geocodificação e cancelamento tardio continuam pendentes. PATCH de rascunho é extensão local para o CRUD, aguardando formalização antes da integração. Não houve configuração de CI global ou publicação do ambiente. Contratos e migrações requerem revisão antes do merge.

A API GitHub encontrou bloqueio do proxy CONNECT HTTP 403 para api.github.com; isso não comprova ausência de credenciais Git. O resultado de push/PR é informado separadamente no relatório final.

## Entrega Git

O push de `feat/p1-sprint-1` foi concluído via autenticação Git fornecida pela plataforma. A execução de `scripts/create-pr.sh` tentou abrir PR para `develop`, mas recebeu `Post https://api.github.com/graphql: Forbidden`. O PR não foi criado. Abrir manualmente a comparação `https://github.com/vondenryan/sistema-logistica/compare/develop...feat/p1-sprint-1?expand=1`, usando `docs/PR.md` como descrição. Não houve merge nem publicação do ambiente.
