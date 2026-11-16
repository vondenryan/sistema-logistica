# Plano Sprint 3 — trabalho independente

A branch `feat/p1-sprint-3` nasceu de `origin/develop` em `e510adc`, após merge humano do PR #4. Escopo: estabilizar P1 existente, sem integrações reais da Sprint 4 ou mudança dos contratos v1 e migrações V1–V6.

Três subagentes `gpt-6.1-terra`, raciocínio `medium`, receberam caminhos de escrita exclusivos e testes com fakes próprios:

| Frente | Arquivos próprios | Entrega |
| --- | --- | --- |
| Backend | `backend/**` | Fakes, autorização, erros, transações, recuperação e testes Java locais |
| Frontend | `frontend/**` | Sessão e respostas atrasadas, estado de pedidos/rotas, testes HTTP/Leaflet fake e build |
| Operações e contratos | Novos scripts Sprint 3, `contract-tests/**`, `INTEGRATION.md`, plano e operações Sprint 3 | Verificador offline descartável, teste do cliente com stub e inventário das dez portas |
| Coordenador | Base Git, README, proposta de PR, evidências e revisão | Verificação real com PostgreSQL descartável, Chromium e regressões finais |

As frentes executaram testes independentemente sem esperar entregas umas das outras. O verificador copia fontes e cache para não alterar o target compartilhado. Os testes com stub são identificados separadamente da execução com backend/PostgreSQL reais. Os arquivos Supabase pendentes foram preservados fora da entrega. Não houve merge pelo agente nem publicação.
