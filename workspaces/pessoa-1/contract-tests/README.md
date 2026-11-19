# Contratos locais — Sprint 1

Esta cópia de trabalho não modifica `contracts/v1/` compartilhado. Exemplos foram extraídos literalmente dos blocos JSON de `../docs/CONTRACTS_V1.md`.

```sh
python3 -m venv contract-tests/.venv
contract-tests/.venv/bin/pip install -r contract-tests/requirements.txt
contract-tests/.venv/bin/python -m unittest discover -s contract-tests -v
```

Os schemas Draft 2020-12 verificam endereço, origem, criação de pedido e Problem Details. A suíte cobre fixtures válidas e variações inválidas. Esta cobertura é parcial: contratos de rotas/frota/documentos serão ampliados nas Sprints correspondentes; não representa integração entre os três integrantes.

Com backend dev e PostgreSQL iniciados, execute `python3 contract-tests/check_live.py` para verificar schemas de erro, autorização por papel/loja, edição/cancelamento de rascunho e confirmação repetida. Essa verificação usa somente dados fictícios e cria pedidos locais.

## Sprint 2

A suíte acrescenta schemas locais de cotação, solicitação/sugestão/snapshot de rota, snapshots de loja, atribuição e entrega. Origem e endereço continuam cobertos pela Sprint 1. Os exemplos explícitos de cotação, rota e entrega reproduzem os blocos de `CONTRACTS_V1.md`; os demais são fixtures locais derivadas das assinaturas, pois o documento não fornece todos os exemplos completos. As respostas aceitam campos adicionais, requests os rejeitam. A matriz cobre enums, nulabilidade e strings decimais; regras dependentes de persistência e cálculo exigem testes de implementação.

`test_smoke_sprint2.py` executa o cliente contra um servidor HTTP stub local: é um teste offline do cliente, não de integração real. Para o procedimento com PostgreSQL/backend locais, consulte `../docs/SPRINT_2_OPERATIONS.md`.

A origem da cotação é uma string não vazia, sem enum congelado: a fixture canônica usa `OSRM` e o stub offline usa `FAKE`. O smoke valida campos principais da cotação com a biblioteca padrão e exige `quoteId` estável na repetição; não depende de jsonschema em execução.
