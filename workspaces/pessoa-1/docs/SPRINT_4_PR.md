## Escopo: preparação parcial da Sprint 4

As Pessoas 2 e 3 ainda não entregaram módulos, conforme confirmado pelo usuário. Esta mudança prepara o empacotamento e a infraestrutura da Pessoa 1 para a composição futura; **não conclui a Sprint 4 nem comprova integração sem fakes**.

Três subagentes Terra 6.1 no médio trabalharam em áreas separadas: backend, frontend e operações/inventário. A branch foi criada da develop após o merge do PR #5 e preserva sua correção de navegação.

## Entrega

- Imagem Java 21 do JAR pré-compilado, usuário não root e exclusão de arquivos locais do contexto de build.
- Imagem Nginx do frontend pré-compilado, SPA, proxy `/api/v1` e políticas de cache.
- Compose de integração separado do banco local, com PostgreSQL, backend, frontend e Mailpit.
- Guardas de perfil que recusam fakes em integration/prod e indicam portas reais ausentes.
- Inventário com saída não zero para P2/P3 ausentes; documentação de operação e pendências.

## Validação

Ver `docs/SPRINT_4_VALIDATION.md` para comandos, contagens e limites. Os testes de container do P1 usam dev/fakes explicitamente apenas para verificar empacotamento/gateway; não são integração real P2/P3. A mensagem sintética capturada no Mailpit verifica o transporte local, sem representar notificação gerada pela aplicação.

## Bloqueios para conclusão

São necessárias as implementações reais de partners/fleet e deliveries/notifications/documents, telas correspondentes, compatibilidade de contratos/build, composição do monólito e coordenação das migrações. E2E pedido→entrega→rastreamento→e-mail/PDF sem fakes permanece pendente. Não há implementação substituta dos módulos alheios, merge ou publicação pelo agente.
