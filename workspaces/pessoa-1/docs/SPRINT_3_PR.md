## Problema e comportamento

O módulo isolado da Pessoa 1 já cobre pedidos, frete e rotas, mas precisa demonstrar estabilidade em falhas, autorização e reexecução sem serviços externos antes da integração. Esta Sprint estabiliza o comportamento existente, amplia testes de regressão e adiciona uma verificação automatizada em ambiente descartável.

A branch parte da develop após o merge do PR #4, incluindo a correção que isola a cotação por pedido. Backend, frontend e operações/contratos foram trabalhados por três subagentes Terra 6.1 no médio em caminhos separados, com testes independentes; o coordenador revisou e validou a composição.

## Validação

Consultar `docs/SPRINT_3_VALIDATION.md` para comandos, contagens, falhas diagnosticadas e limites efetivamente verificados. Os testes usam fakes determinísticos e banco local; não comprovam integração com as Pessoas 2/3. O verificador deve funcionar sem servidores OSRM, Supabase, geocodificação ou outros módulos, usando dependências previamente instaladas.

## Limites

Não há implementação das regras internas de outros integrantes nem alteração silenciosa dos contratos congelados. A tarifa de implantação, geocodificação e regras de cancelamento ainda pendentes continuam explícitas. O ciclo terminal de rotas e a transição de entrega ao iniciar uma rota seguem as limitações documentadas da versão v1. A substituição de fakes por adaptadores reais e a composição final permanecem na Sprint 4.

Não habilitar auto-merge, fazer merge pelo agente ou publicar o ambiente. Revisar segurança, transações, migrações eventualmente novas e interfaces de integração antes do merge.
