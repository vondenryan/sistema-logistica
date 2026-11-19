## Problema e comportamento

A Sprint 1 permite autenticar e gerenciar pedidos isoladamente, mas ainda não oferece cotação de frete nem operação de rotas. Esta Sprint acrescenta cotação com snapshot histórico, distância OSRM configurável, demonstração offline com fake explícito, planejamento/atribuição de rotas e telas Vue com mapa e coordenadas manuais.

## Implementação

Três subagentes Terra 6.1 no médio trabalharam em áreas exclusivas: backend, frontend e operações/contratos. Cada frente foi desenvolvida e testada com fakes próprios; o coordenador validou a composição com PostgreSQL local. A branch foi criada da develop após os merges das Sprints anteriores.

- Tarifa global obrigatoriamente configurável, dinheiro decimal e arredondamento explícito; sem valor definitivo presumido para implantação.
- Rotas de até dez pedidos confirmados da mesma loja/origem, sugestão, reordenação antes do início e atribuição por portas fake.
- Mapa Leaflet/OSM e entrada manual de coordenadas sem depender de geocodificação externa.
- Novas migrações locais e testes de contrato; os contratos compartilhados e V1–V4 permanecem preservados.

## Validação e limites

Consultar `docs/SPRINT_2_VALIDATION.md` para resultados efetivamente executados. OSRM público e Supabase estão sujeitos às limitações de rede do ambiente; testes offline e servidor OSRM de teste não comprovam disponibilidade externa. A tarifa usada na validação é exclusivamente uma fixture. A integração com os produtores reais das Pessoas 2/3 permanece na Sprint 4. Revisar migrações, concorrência, autorização e snapshots antes do merge. Não habilitar auto-merge nem publicar o ambiente.
