# Plano de trabalho — Sprint 1

Branch existente `feat/p1-sprint-1`; três fatias independentes em caminhos distintos, sem esperar saída de outra fatia e usando fixtures/test doubles locais. A especificação compartilhada é [CONTRACTS_V1.md](CONTRACTS_V1.md), congelada localmente; em conflito com o plano de Sprint ela prevalece. O DAT aprovado não está disponível, portanto decisões de negócio ausentes permanecem pendentes.

1. **Backend:** identity, orders, billing e routing; persistência, autenticação/autorização e todas as portas/fakes necessárias para execução isolada, com testes próprios e fixtures locais.
2. **Frontend:** shell, login e pedidos, orientado por fixtures/contrato e testes próprios, sem depender do backend em construção.
3. **Operações:** Compose PostgreSQL, geração de segredos locais, scripts de execução/teste/smoke e documentação. Validar scripts e Compose com ambiente temporário, sem depender dos demais componentes.

A integração é responsabilidade do coordenador no projeto pai: compor os adaptadores por portas em processo, validar contratos e substituir fakes pelos produtores reais descritos em [INTEGRATION.md](../INTEGRATION.md). Não criar microsserviços HTTP para comunicação entre módulos. Após juntar as fatias, executar os testes reais e smoke; resultados isolados não demonstram integração. Não presumir tarifa inicial, provedor de geocodificação ou regras de cancelamento tardio ausentes do DAT.
