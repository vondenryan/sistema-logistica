# Sprint 4 — preparação parcial P1

Estado: **parcial, integração não concluída**. O usuário confirmou que P2 e P3 ainda não entregaram fontes. Este trabalho prepara a topologia e um inventário, sem implementar módulos de outras pessoas nem suprir suas portas com fakes. Os contratos v1 e `INTEGRATION.md` continuam sendo as referências. A composição de backend, frontend e Mailpit não comprova o aceite.

Próximos passos após as entregas reais:

1. Inventariar fontes, recursos, contratos, fixtures e versões das três pessoas; executar testes de contrato de consumidor e provedor. Conferir a versão comum de Spring Boot e compatibilidade dos builds.
2. Compor os módulos reais no monólito, ligar PartnerLookup, FleetAssignment/Release, DeliveryCommand/Status, NotificationRequest e DocumentRequest e conectar P2/P3 ao JWT e aos snapshots P1. Não usar fakes no perfil integrado; testar autorização e falhas de dependências.
3. Inventariar históricos Flyway e conflitos de versões antes de definir a sequência global. Preservar V1–V6 já aplicadas; não editar, renomear ou reaplicar versões globais agora. Planejar migrações novas e testar banco limpo e histórico preservado com dados e constraints.
4. Integrar as telas reais e gateway frontend; testar builds, contratos, segurança, idempotência, indisponibilidade e recuperação com OSRM e SMTP reais para a composição.
5. Executar E2E real pedido → frete → rota → atribuição → entrega → rastreamento → e-mail no Mailpit → PDF simulado. Só então avaliar o aceite da Sprint 4.

O inventário local é uma convenção preliminar de caminhos, não uma validação das implementações; ver [operações](SPRINT_4_OPERATIONS.md). Cada frente pode executar seus testes com fixtures P1 sem aguardar outra entrega, mas isso não substitui testes entre módulos reais.

A preparação foi dividida em três subagentes `gpt-6.1-terra`, raciocínio `medium`, com áreas exclusivas: backend/perfis/imagem Java; frontend/Nginx/imagem estática; Compose/inventário/documentação. O coordenador revisou e executou os testes reais dos containers, mantendo separados os resultados de componentes P1 e a integração real ainda bloqueada.
