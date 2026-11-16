# Sprint 2 — operação local

Os contratos normativos permanecem em `CONTRACTS_V1.md`. A tarifa global é uma decisão pendente: o valor `2.50` nas fixtures é apenas exemplo e não é padrão de implantação. A demonstração usa roteamento fake, não OSRM ou Supabase.

Com PostgreSQL local e o `.env` local já configurados, escolha explicitamente uma tarifa para a demonstração e inicie o backend em um terminal:

```sh
cd workspaces/pessoa-1
FREIGHT_TARIFF_PER_KM='<tarifa da demonstração>' scripts/demo-sprint2.sh
```

O launcher carrega o `.env`, exige DB_PASSWORD, JWT_SECRET, DEMO_PASSWORD e FREIGHT_TARIFF_PER_KM, ativa `ROUTING_PROVIDER=fake` e inicia o backend no perfil dev, porta 8080. Não altera o ambiente salvo. O `.env` é carregado como shell pelo launcher: use somente arquivo local confiável. O valor da tarifa também pode estar no `.env` existente; não imprima credenciais.

Em outro terminal, com o backend pronto:

```sh
cd workspaces/pessoa-1
python3 scripts/smoke-sprint2.py
```

O smoke lê `.env` como atribuições simples com `shlex`, sem executar comandos. Variáveis já exportadas prevalecem. `DEMO_PASSWORD` é obrigatório; `SMOKE_EMAIL`, `SMOKE_STORE_ID`, `SMOKE_ORIGIN_ID` e `SMOKE_BASE_URL` podem ajustar os padrões locais. Ele cria dois pedidos fictícios, confirma cada um duas vezes, cota duas vezes e verifica o mesmo quoteId, sugere e persiste uma rota, inverte as paradas, atribui duas vezes, inicia e consulta. Usa uma única rota para funcionar com frota fake de um veículo. Ele cria dados persistentes; execute em banco local de teste.

```sh
python3 -m unittest discover -s contract-tests -v
```

Essa suíte valida schemas e o cliente smoke contra um servidor HTTP stub local. O stub não valida PostgreSQL, backend real, OSRM, concorrência nem integração entre as pessoas. A execução do smoke contra serviços reais deve ser relatada separadamente, com suas condições e resultados. A integração efetiva das portas permanece para a etapa de integração.
