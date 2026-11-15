# Contratos locais — Sprint 1

Esta cópia de trabalho não modifica `contracts/v1/` compartilhado. Exemplos foram extraídos literalmente dos blocos JSON de `../docs/CONTRACTS_V1.md`.

```sh
python3 -m venv contract-tests/.venv
contract-tests/.venv/bin/pip install -r contract-tests/requirements.txt
contract-tests/.venv/bin/python -m unittest discover -s contract-tests -v
```

Os schemas Draft 2020-12 verificam endereço, origem, criação de pedido e Problem Details. A suíte cobre fixtures válidas e variações inválidas. Esta cobertura é parcial: contratos de rotas/frota/documentos serão ampliados nas Sprints correspondentes; não representa integração entre os três integrantes.

Com backend dev e PostgreSQL iniciados, execute `python3 contract-tests/check_live.py` para verificar schemas de erro, autorização por papel/loja, edição/cancelamento de rascunho e confirmação repetida. Essa verificação usa somente dados fictícios e cria pedidos locais.
