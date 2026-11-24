# Back-end — Projeção Orçamentária e EBITDA

Java 21 + Spring Boot 3.4. Sem banco de dados: a base vem de CSVs e as alterações ficam em
`dados-runtime/`. **Todos os dados são fictícios.**

## Rodar

Precisa do **JDK 21** (não basta o JRE). No Ubuntu:

```bash
sudo apt install openjdk-21-jdk-headless
```

```bash
./mvnw spring-boot:run
```

API em `http://localhost:8080/api`. Se existir a pasta `../frontend`, ela é servida em
`http://localhost:8080/`. Para o front rodar separado (Live Server etc.), o CORS já libera
`localhost` e `127.0.0.1` em qualquer porta.

```bash
./mvnw test
```

```bash
./mvnw clean package && java -jar target/itaqui-ebitda-0.1.0.jar
```

Para começar do zero, apague `dados-runtime/` (ou chame `POST /api/base/restaurar`).

## Estrutura

```
src/main/java/br/jovemtech/itaqui/
  dominio/   Conta, Periodo, Premissa, Vinculo, Cenario, Base (imutável)
  calculo/   MOTOR em Java puro — MotorCalculo, MotorForecast, AgregadorDre,
             CalculadoraEbitda (fórmula), RegraProjecao / ProjecaoPorIndice
  dados/     CarregadorBase (lê e valida CSV), CsvEscritor, repositórios
  servico/   ServicoBase, ServicoCenario
  api/       controllers, DTOs, TratadorErros
src/main/resources/dados/   base fictícia (gerada por scripts/gerar_base_ficticia.py)
src/test/                   CT-01..CT-07, testes da API, confronto com o Oráculo
```

## Regras implementadas

| Regra | Onde |
|---|---|
| EBITDA = (Receita Bruta − Deduções) − Custos − Despesas Operacionais; D&A fora | `CalculadoraEbitda` |
| Margem = EBITDA / Receita Líquida × 100 | `CalculadoraEbitda` |
| Mês ≤ data de corte = FECHADO → forecast = realizado (nunca muda) | `MotorForecast` |
| Mês aberto: `projetado = orçado × (1 + índice_cenário/100) / (1 + índice_original/100)` | `ProjecaoPorIndice` |
| Conta sem premissa vinculada: projetado = orçado (RN005) | `MotorForecast` |
| Cenário guarda só premissas alteradas; orçamento e realizado são imutáveis | `Cenario`, `Base` |
| Cenário `original` é somente leitura (HTTP 409) | `ServicoCenario` |
| Ajuste em mês fechado é recusado (HTTP 422 `PERIODO_FECHADO`) | `ServicoCenario` |
| Importação tudo ou nada (HTTP 422 com lista de erros) | `CarregadorBase` |

Cálculo em precisão cheia (`BigDecimal`), arredondado a 2 casas (`HALF_UP`, igual ao `ARRED`
do Excel) só na saída.

## Endpoints

Todos devolvem JSON, exceto os `.csv`. Erros sempre no formato
`{"erro": "CODIGO", "mensagem": "...", "detalhes": [...]}`.

### Informações gerais
| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/meta` | data de corte, anos, `dadosFicticios`, aviso para exibir, fórmulas em uso |

### Estrutura da DRE (RF001)
| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/dre/estrutura` | árvore de contas com premissas vinculadas |
| POST | `/api/dre/contas` | `{codigo, nome, codigoPai?, tipo, opex?, ordem?}` → 201 |
| DELETE | `/api/dre/contas/{codigo}` | só contas sem filhas e sem valores → 204 |

`tipo`: `GRUPO`, `RECEITA_BRUTA`, `DEDUCAO_RECEITA`, `CUSTO`, `DESPESA_OPERACIONAL`, `DEPRECIACAO_AMORTIZACAO`.

### Premissas e vínculos (RF003, RF004)
| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/premissas` | premissas do orçamento original, mês a mês |
| GET | `/api/premissas/{codigo}` | uma premissa |
| POST | `/api/premissas` | `{codigo, nome, valores:[{periodo:"2026-10", percentual:4.5}]}` → 201 |
| GET | `/api/vinculos` | vínculos conta → premissa |
| POST | `/api/vinculos` | `{codigoConta, codigoPremissa, vigenciaInicio:"2026-10"}` → 201 |
| DELETE | `/api/vinculos?conta=&premissa=` | → 204 |

### Cenários e cálculo (RF005–RF009)
| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/cenarios` | lista (o primeiro é sempre `original`) |
| GET | `/api/cenarios/{id}` | um cenário com seus ajustes |
| POST | `/api/cenarios` | `{nome, copiarDe?}` → 201 |
| PATCH | `/api/cenarios/{id}` | `{nome}` renomeia |
| DELETE | `/api/cenarios/{id}` | → 204 |
| GET | `/api/cenarios/{id}/premissas` | cada premissa: original × cenário, mês a mês, com status |
| PUT | `/api/cenarios/{id}/premissas/{codigo}` | `{ajustes:[{periodo, percentual}]}` — só meses abertos |
| DELETE | `/api/cenarios/{id}/premissas/{codigo}` | desfaz os ajustes dessa premissa |
| GET | `/api/cenarios/{id}/dre?ano=2026` | **DRE completa**: linhas conta × mês (orçado, realizado, projetado, forecast), totais, EBITDA e margem do ano e de cada mês, `tempoCalculoMs` |
| GET | `/api/cenarios/{id}/comparativo?ano=2026&base=original` | diferenças de forecast por conta/mês e nos indicadores |

Na resposta da DRE: `realizado` só vem preenchido em meses `FECHADO`, `projetado` só em
meses `ABERTO`; `forecast` sempre. Nas linhas, `total.orcadoAteCorte` é o orçado dos meses
fechados, para comparar com o realizado.

### Base, importação e evidências (RF002, RF011, CT-06, CT-07)
| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/importacao?simular=false` | multipart com as partes `config`, `contas`, `valores`, `premissas`, `vinculos`. 200 se válida, 422 com a lista de erros (base atual intacta) |
| GET | `/api/base/arquivos/{nome}.csv` | baixa um arquivo da base atual (modelo para a equipe de Dados) |
| POST | `/api/base/restaurar` | volta para a base fictícia do projeto |
| GET | `/api/evidencias/indicadores.csv?cenario=original&ano=2026` | indicadores no layout do Oráculo |
| GET | `/api/evidencias/contas.csv?cenario=original&ano=2026` | DRE conta × mês |
| GET | `/api/evidencias/desempenho?ano=2026&repeticoes=20` | CT-06: tempo mín/médio/máx do recálculo |

Exemplo de importação com curl:

```bash
curl -F config=@config.csv -F contas=@contas.csv -F valores=@valores.csv -F premissas=@premissas.csv -F vinculos=@vinculos.csv "http://localhost:8080/api/importacao?simular=true"
```

## Formato dos CSVs (equipe de Dados)

Separador `;`, UTF-8, cabeçalho obrigatório, decimal com vírgula ou ponto (**sem** separador de
milhar), valores sempre positivos (o sinal vem do tipo da conta).

| Arquivo | Colunas |
|---|---|
| `config.csv` | `chave;valor` → `data_corte` (obrigatória, AAAA-MM), `origem`, `unidade`, `versao` |
| `contas.csv` | `codigo;nome;codigo_pai;tipo;opex;ordem` (`opex` = S/N) |
| `valores.csv` | `codigo_conta;ano;mes;tipo;valor` (`tipo` = ORCADO/REALIZADO) |
| `premissas.csv` | `codigo;nome;ano;mes;percentual` — índice que o orçamento original considerou no mês |
| `vinculos.csv` | `codigo_conta;codigo_premissa;vigencia_inicio` |

Erros recusados: conta/premissa inexistente, código duplicado, tipo desconhecido, valor em
GRUPO, grupo misturando tipos, valor negativo ou não numérico, mês fora de 1–12, REALIZADO
depois da data de corte, lançamento duplicado, percentual ≤ −100, `data_corte` ausente.

A base de exemplo pode ser regerada com `python3 scripts/gerar_base_ficticia.py src/main/resources/dados`.

## Confronto com a Planilha Oráculo

Veja `src/test/resources/oraculo/README.md`. Quando o `esperado.csv` for colocado lá,
`./mvnw test` passa a conferir o motor contra a planilha automaticamente.
