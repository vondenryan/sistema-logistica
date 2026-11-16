# Sprint 2 — plano de trabalho independente

Base: `feat/p1-sprint-2` criada de `origin/develop` verificado. Uma branch para a Sprint; ao terminar, preparar um único PR para `develop`, sujeito a revisão e merge humanos. Não publicar, fazer push ou merge automaticamente nesta execução. Contratos v1, CI global e arquivos fora de Pessoa 1 permanecem fora do escopo.

Três frentes independentes, originalmente solicitadas como subagentes Terra 6.1 medium, trabalham em arquivos disjuntos e usam fakes e fixtures canônicas para compilar e testar sem aguardar as outras:

1. **Frete e geografia:** cotação com tarifa configurada, distância e roteamento por porta, persistência e testes offline. Nenhuma tarifa de implantação presumida.
2. **Rotas e interface:** sugestão, persistência, reordenação, atribuição/início e experiência do operador, com portas fake e testes próprios.
3. **Contratos e operação:** schemas, fixtures, matriz de testes, launcher e smoke contra servidor fake; operações e limites documentados aqui.

A pessoa integradora verifica o conjunto, roda o smoke real com PostgreSQL local e serviços disponíveis, e revisa o Git. Testes de contrato e stub não substituem essa verificação. Nenhuma frente depende de Supabase remoto ou altera credenciais locais.
