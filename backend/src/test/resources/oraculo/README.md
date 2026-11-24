# Confronto com a Planilha Oráculo

Coloque aqui os arquivos exportados da Planilha Oráculo. O teste `OraculoTest` roda
automaticamente quando `esperado.csv` existir (senão é ignorado).

A base usada é a de `src/main/resources/dados/`, então a planilha deve partir dos mesmos CSVs.

## esperado.csv

```
cenario;ano;mes;indicador;perspectiva;valor
original;2026;1;ebitda;orcado;15321,45
original;2026;TOTAL;margem_ebitda_pct;forecast;43,12
```

- `mes`: 1 a 12, ou `TOTAL`
- `indicador`: receita_bruta, deducoes, receita_liquida, custos, despesas_operacionais, ebitda,
  margem_ebitda_pct, depreciacao_amortizacao
- `perspectiva`: orcado, realizado, forecast
- `valor`: arredondado a 2 casas (`=ARRED(x;2)`), vírgula ou ponto

O layout é o mesmo de `GET /api/evidencias/indicadores.csv`.

## ajustes.csv (opcional, para testar cenários — CT-03)

```
cenario;codigo_premissa;periodo;percentual
ipca_alto;IPCA;2026-10;8,00
```

Regra de projeção usada pelo sistema (replique na planilha):
`=ORCADO*(1+INDICE_CENARIO/100)/(1+INDICE_ORIGINAL/100)`
