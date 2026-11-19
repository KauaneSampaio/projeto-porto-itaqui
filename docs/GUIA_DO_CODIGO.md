# Guia do código — Plataforma de Projeção Orçamentária e EBITDA

Este documento explica **cada parte do código**: para que serve, como foi feita e por que foi
feita assim. Para colocar o sistema para rodar, veja [COMO_RODAR.md](COMO_RODAR.md).

Leitura sugerida: seções 1 a 3 primeiro (visão geral, conceitos e o caminho de uma requisição).
Depois, consulte o back-end (seção 4) e o front-end (seção 5) quando precisar.

---

## Sumário

1. [Visão geral](#1-visão-geral)
2. [Conceitos do domínio que o código usa](#2-conceitos-do-domínio-que-o-código-usa)
3. [O caminho de uma requisição, do clique ao número](#3-o-caminho-de-uma-requisição-do-clique-ao-número)
4. [Back-end](#4-back-end)
   - 4.1 [Arquivos de projeto (pom.xml, mvnw, application.properties)](#41-arquivos-de-projeto)
   - 4.2 [Inicialização e configuração (`Aplicacao`, `config/`)](#42-inicialização-e-configuração)
   - 4.3 [Domínio (`dominio/`)](#43-domínio-dominio)
   - 4.4 [Motor de cálculo (`calculo/`)](#44-motor-de-cálculo-calculo)
   - 4.5 [Dados: leitura, validação e gravação (`dados/`)](#45-dados-leitura-validação-e-gravação-dados)
   - 4.6 [Serviços (`servico/`)](#46-serviços-servico)
   - 4.7 [API REST (`api/`)](#47-api-rest-api)
   - 4.8 [Base fictícia e gerador](#48-base-fictícia-e-gerador)
   - 4.9 [Testes](#49-testes)
5. [Front-end](#5-front-end)
   - 5.1 [Estrutura e ordem de carregamento](#51-estrutura-e-ordem-de-carregamento)
   - 5.2 [index.html e CSS](#52-indexhtml-e-css)
   - 5.3 [Núcleo: base.js, api.js, estado.js, graficos.js, main.js](#53-núcleo)
   - 5.4 [As telas (`js/telas/`)](#54-as-telas-jstelas)
6. [Decisões de projeto e por quê](#6-decisões-de-projeto-e-por-quê)
7. [Onde mexer para…](#7-onde-mexer-para)
8. [Limitações conhecidas e pendências](#8-limitações-conhecidas-e-pendências)

---

## 1. Visão geral

```
┌──────────────────────── Navegador ────────────────────────┐
│  frontend/  (HTML + CSS + JavaScript puro + Chart.js)      │
│  telas ──► estado.js ──► api.js ── fetch JSON ──┐          │
└─────────────────────────────────────────────────┼──────────┘
                                                  ▼
┌───────────────── backend/ (um único processo Java) ─────────────────┐
│  api/        Controllers: recebem HTTP, validam, devolvem JSON       │
│     │                                                                │
│  servico/    Regras de uso: "cenário original não pode ser editado", │
│     │        "ajuste só em mês aberto", cadastro de conta...         │
│     ├────────────► calculo/   MOTOR (Java puro): DRE, forecast, EBITDA│
│     │                                                                │
│  dados/      Repositórios: base atual + cenários; leitura/validação   │
│     │        e gravação dos CSV/JSON                                  │
│  dominio/    Os "objetos" do negócio: Conta, Periodo, Premissa...     │
└─────┼────────────────────────────────────────────────────────────────┘
      ▼
  resources/dados/*.csv   (base fictícia inicial, vem dentro do programa)
  dados-runtime/          (base editada/importada e cenarios.json — criada ao rodar)
```

Números do projeto (sem contar o `style.css` original da equipe):

| Parte | Arquivos | Linhas |
|---|---|---|
| Back-end, código | 57 `.java` | ≈ 3.300 |
| Back-end, testes | 6 `.java` | ≈ 940 |
| Front-end | 1 HTML, 1 CSS novo, 11 JS | ≈ 3.500 |

**Princípios que guiam tudo:**

1. **Um processo só.** O Spring Boot serve a API (`/api/...`) e também os arquivos do front.
2. **O motor de cálculo não sabe que existe Spring nem arquivos.** Recebe dados e devolve
   números. Por isso é fácil de testar.
3. **Imutável onde importa.** A base (orçado e realizado) nunca é alterada "por dentro":
   qualquer mudança gera uma base nova. Um cenário não guarda valores, só os índices que mudou.
   Assim, é **impossível** um cenário estragar o orçamento original ou o realizado.
4. **Recalcular sempre.** Não há cache no servidor: cada consulta recalcula tudo, em cerca de 1 ms.

---

## 2. Conceitos do domínio que o código usa

| Termo | No código | Significado |
|---|---|---|
| DRE | `Conta`, `LinhaDre` | Demonstração do Resultado: lista hierárquica de contas (receitas, deduções, custos, despesas) |
| Conta folha | `Conta.folha()` | Conta que recebe valores (ex.: "5.1 Pessoal operacional") |
| Grupo | `TipoConta.GRUPO` | Só soma as filhas (ex.: "5 Custos dos Serviços") |
| Período | `Periodo` | Um mês de um ano, escrito `AAAA-MM` |
| Data de corte | `Base.dataCorte()` | Último mês **fechado** contabilmente (hoje `2026-09`) |
| Mês fechado / aberto | `StatusPeriodo` | Fechado: até a data de corte. Aberto: depois dela |
| Orçado | `TipoValor.ORCADO` | Orçamento original. Nunca muda |
| Realizado | `TipoValor.REALIZADO` | O que de fato aconteceu. Só existe em mês fechado |
| Projetado | `ValoresMes.projetado` | Estimativa revisada de um mês aberto, conforme o cenário |
| Forecast | `ValoresMes.forecast` | Realizado nos meses fechados + projetado nos abertos |
| Premissa | `Premissa` | Índice que o orçamento considerou (IPCA, IGP-M, reajuste salarial, tarifa), em % por mês |
| Vínculo | `Vinculo` | "A conta X segue a premissa Y a partir do mês Z" |
| Cenário | `Cenario` | Simulação: troca o valor de premissas em meses abertos. O "original" é somente leitura |
| EBITDA | `CalculadoraEbitda` | (Receita bruta − deduções) − custos − despesas operacionais. Depreciação e amortização ficam fora |
| OPEX | `Conta.opex()` | Marca de despesa operacional, para destacar na tela |

**Exemplo numérico** (é a base usada nos testes, `BaseTeste.java`). Valores mensais orçados:
receita 1000, deduções 100, custos 300 + 50, despesas 200, depreciação 40.

- Receita líquida = 1000 − 100 = **900**
- EBITDA = 900 − 350 − 200 = **350** por mês, ou 4.200 no ano (a depreciação não entra)
- Margem = 350 ÷ 900 = **38,89%**

Se a conta de custo de 300 segue o IPCA, o orçamento considerou IPCA de 4,5% e o cenário
muda para 10%, a projeção dos meses abertos fica `300 × 1,10 ÷ 1,045 = 315,79`.

---

## 3. O caminho de uma requisição, do clique ao número

Exemplo: o usuário altera o IPCA de outubro para 10% num cenário e clica **"Aplicar e recalcular"**.

1. **Front**: `js/telas/cenarios.js → salvarPremissa()` lê os campos, valida (número entre
   −99,99 e 999,99), monta `[{periodo:"2026-10", percentual:10}]` e chama
   `Api.ajustarPremissa(id, "IPCA", ajustes)`.
2. **Front**: `js/api.js → chamarApi()` faz um `fetch PUT /api/cenarios/{id}/premissas/IPCA`
   com o JSON no corpo e mostra "Calculando…" se demorar mais de 250 ms.
3. **Back**: `api/CenarioController.ajustarPremissa()` recebe a requisição. O Spring converte o
   JSON em `AjustePremissaRequest`, e `@Valid` confere se a lista não está vazia. O texto
   `"2026-10"` vira um `Periodo` por causa do `@JsonCreator` em `Periodo.parse`.
4. **Back**: `servico/ServicoCenario.ajustarPremissa()` aplica as regras:
   - o cenário é o original? Então devolve **409** `CENARIO_SOMENTE_LEITURA`;
   - a premissa existe? Se não, **404**;
   - algum mês é fechado? Então **422** `PERIODO_FECHADO` e nada é gravado.

   Se estiver tudo certo, `Cenario.comAjustes()` gera um **novo** objeto cenário, e
   `RepositorioCenarios.salvar()` guarda na memória e no `cenarios.json`.
5. **Front**: depois de salvar, `aposAlterar()` limpa o cache (`Estado.invalidar()`), recarrega
   a lista de cenários e pede `GET /api/cenarios/{id}/comparativo?ano=2026`.
6. **Back**: `ServicoCenario.comparativo()` chama `MotorCalculo.calcular()` duas vezes (original
   e cenário) e devolve as diferenças.
7. **Motor**: para cada conta folha, `MotorForecast` decide mês a mês:
   - mês **fechado**: usa o realizado;
   - mês **aberto**: procura os vínculos vigentes da conta. Se não houver nenhum,
     projetado = orçado. Se houver, aplica `ProjecaoPorIndice`.

   Depois, `AgregadorDre` soma as folhas nos grupos e `CalculadoraEbitda` calcula receita
   líquida, EBITDA e margem.
8. **Front**: `mostrarImpacto()` exibe "EBITDA forecast 232.588 → 231.619 (−969) · recalculado em N ms".

---

## 4. Back-end

Pasta `backend/`. Pacote raiz: `br.jovemtech.itaqui`.

### 4.1 Arquivos de projeto

| Arquivo | Para que serve | Como foi feito |
|---|---|---|
| `pom.xml` | Define o projeto Maven: Java 21, Spring Boot 3.4.5, dependências | Herda de `spring-boot-starter-parent`, que fixa as versões compatíveis. Dependências: `starter-web` (API REST + servidor Tomcat embutido), `starter-validation` (`@NotBlank` etc.), `commons-csv` (leitura de CSV) e `starter-test` (JUnit, AssertJ, MockMvc) |
| `mvnw`, `mvnw.cmd`, `.mvn/wrapper/` | **Maven Wrapper**: roda o Maven sem instalá-lo | Na primeira execução, baixa o Maven 3.9.9 para `~/.m2/wrapper`. `mvnw` é para Linux/Mac; `mvnw.cmd`, para Windows |
| `src/main/resources/application.properties` | Configurações do servidor | Porta 8080; `static-locations` faz o Spring servir `../frontend/`; `app.dados.diretorio` diz onde gravar; `app.cors.origens` libera o front rodando em outra porta; o Jackson escreve `BigDecimal` sem notação científica; limite de upload de 10 MB |
| `.gitignore` | Não versiona `target/` (compilação) nem `dados-runtime/` (dados gerados) | — |

### 4.2 Inicialização e configuração

| Arquivo | Para que serve | Como foi feito |
|---|---|---|
| `Aplicacao.java` | Ponto de entrada (`main`) | `@SpringBootApplication` liga a varredura automática de componentes; `@ConfigurationPropertiesScan` carrega o `PropriedadesApp` |
| `config/PropriedadesApp.java` | Lê as propriedades `app.*` do `application.properties` de forma tipada | É um `record` com `@ConfigurationProperties(prefix="app")`. Tem valores padrão caso a propriedade falte (diretório `./dados-runtime`, CORS vazio) |
| `config/ConfigWeb.java` | (1) CORS; (2) cria o `MotorCalculo` | O CORS só vale para `/api/**` e só para as origens configuradas (`localhost` e `127.0.0.1` em qualquer porta); outra origem recebe 403. O `@Bean motorCalculo()` é o **único lugar** que escolhe a regra de projeção (`new ProjecaoPorIndice()`). O motor não tem anotação do Spring: quem o "monta" é esta classe |

### 4.3 Domínio (`dominio/`)

São os objetos do negócio. Quase todos são `record`, classes imutáveis do Java em que os campos
não mudam depois de criados.

| Arquivo | Para que serve | Como foi feito / detalhes |
|---|---|---|
| `Periodo.java` | Um mês (`ano`, `mes`) | Valida o mês (1–12) e o ano (1900–2200) no construtor. `parse("2026-09")` lê texto; `toString()` escreve `2026-09`. Com `@JsonCreator`/`@JsonValue`, o JSON usa sempre o formato texto. Implementa `Comparable` via `ano*12+mes`, o que permite ordenar e comparar com `depoisDe()` |
| `TipoConta.java` | Natureza da conta | `GRUPO`, `RECEITA_BRUTA`, `DEDUCAO_RECEITA`, `CUSTO`, `DESPESA_OPERACIONAL`, `DEPRECIACAO_AMORTIZACAO`. `folha()` é falso só para `GRUPO`. **O sinal no cálculo vem daqui**: os valores nos arquivos são sempre positivos |
| `TipoValor.java` | `ORCADO` ou `REALIZADO` | — |
| `StatusPeriodo.java` | `FECHADO` ou `ABERTO` | — |
| `Conta.java` | Uma linha da DRE | `codigo`, `nome`, `codigoPai` (nulo = raiz), `tipo`, `opex`, `ordem` |
| `ValorLancado.java` | Um valor mensal de uma conta | `codigoConta`, `periodo`, `tipo`, `valor` (`BigDecimal`) |
| `Premissa.java` | Índice com um percentual por mês | Guarda um `SortedMap<Periodo, BigDecimal>` não modificável. `percentual(p)` devolve **0 se o mês não tiver valor** |
| `Vinculo.java` | Conta → premissa a partir de um mês | `vigente(p)` é verdadeiro se `p` é igual ou posterior ao início |
| `Base.java` | **Toda a base carregada**: contas, valores, premissas, vínculos, data de corte, origem | Classe `final` imutável. No construtor monta **índices** para consultas rápidas: mapa de contas por código, filhos de cada grupo, raízes ordenadas por `ordem`, valores por (conta, mês, tipo), vínculos por conta e anos existentes. `fechado(p)` = `p` não é depois do corte. Para "alterar", existem métodos que **devolvem uma base nova**: `comConta`, `semConta`, `comPremissa`, `comVinculo`, `semVinculos`, `comFonte` |
| `Cenario.java` | Simulação | `id`, `nome`, `somenteLeitura`, datas e `ajustes` (premissa → mês → %). **Não tem valores de contas.** `percentual(premissa, mes)` devolve o ajuste, se houver, ou o original. `comAjustes`/`semAjustes`/`comNome` devolvem um cenário novo. `Cenario.original()` cria o cenário fixo de id `original` |

### 4.4 Motor de cálculo (`calculo/`)

É o coração do sistema. **Java puro**: não importa nada do Spring nem lê arquivos.

| Arquivo | Para que serve | Como foi feito |
|---|---|---|
| `MotorCalculo.java` | **Ponto de entrada**: `calcular(base, cenario, ano)` → `ResultadoDre` | (1) Para cada conta folha, pede a série de 12 meses ao `MotorForecast`; (2) o `AgregadorDre` soma os grupos; (3) monta as linhas na ordem da árvore (busca em profundidade a partir das raízes) com `nivel`; (4) agrupa as folhas por tipo e pede os indicadores mensais e anuais à `CalculadoraEbitda`; (5) mede o tempo com `System.nanoTime()` e devolve `tempoCalculoMs` |
| `MotorForecast.java` | Valores de **uma conta folha** nos 12 meses | Para cada mês: orçado = valor carregado (ou 0). **Fechado**: realizado = valor carregado (ou 0) e forecast = realizado; projetado fica nulo. **Aberto**: monta a lista de `IndiceAplicado` com os vínculos vigentes; se vazia, projetado = orçado (**RN005**); senão, aplica a `RegraProjecao`. Realizado fica nulo |
| `RegraProjecao.java` | Interface: "como projetar um mês aberto" | Existe porque o método ainda depende de validação da GFin (RN004). Para trocar a regra: crie outra classe que implemente a interface e mude a linha em `ConfigWeb` |
| `ProjecaoPorIndice.java` | Regra em uso | `projetado = orçado × (1 + novo/100) ÷ (1 + original/100)`. Ideia: o orçado já embute o índice original; o cenário troca pelo novo. Com duas premissas, os fatores se multiplicam. Se o índice não mudou, o fator é 1 (então **no cenário original, projetado = orçado**). Conta em `MathContext.DECIMAL128` (34 dígitos) |
| `IndiceAplicado.java` | Dados de um índice num mês | `codigoPremissa`, `percentualOriginal`, `percentualCenario` |
| `AgregadorDre.java` | Soma das contas folha nos grupos | Recursivo: a série de um grupo é a soma das séries das filhas, e funciona em qualquer profundidade. Um grupo começa com zeros (realizado zero nos meses fechados, projetado zero nos abertos), para não ficar nulo se não tiver filhas |
| `CalculadoraEbitda.java` | **Único lugar com a fórmula do EBITDA** (RN001) | Soma por tipo e calcula: receita líquida = bruta − deduções; EBITDA = líquida − custos − despesas operacionais; margem = EBITDA ÷ receita líquida × 100 (nula se a receita for 0). Faz isso para as três perspectivas (orçado, realizado, forecast), mês a mês (`doMes`) e no ano (`doAno`). No ano, o realizado é o **acumulado dos meses fechados** |
| `ResultadoDre.java` | Formato da resposta do motor (vai direto para o JSON) | `records` aninhados: `MesInfo`, `ValoresMes` (orcado, realizado, projetado, forecast), `TotaisLinha` (inclui `orcadoAteCorte`, para comparar com o realizado), `LinhaDre`, `Perspectivas`, `Indicadores`, `IndicadoresMes` |
| `Serie.java` | Uso interno: 4 vetores de 12 posições (orçado, realizado, projetado, forecast) em precisão cheia | `somar()` soma outra série posição a posição, respeitando nulos |
| `Valores.java` | Uso interno: aritmética com nulos e arredondamento | `soma` (nulo + x = x), `subtrai`, `percentual` e `r2()`, que arredonda para 2 casas com `HALF_UP` (como o `ARRED` do Excel). **Só se arredonda na saída**: os cálculos usam precisão cheia |

**Por que nulos?** Nulo significa "não se aplica": o realizado de um mês aberto e o projetado de
um mês fechado. O front mostra "—" nesses casos.

### 4.5 Dados: leitura, validação e gravação (`dados/`)

| Arquivo | Para que serve | Como foi feito |
|---|---|---|
| `ArquivosBase.java` | Nomes dos 5 CSV: `config`, `contas`, `valores`, `premissas`, `vinculos` | Constantes |
| `CarregadorBase.java` | **Lê e valida** os 5 CSV e monta uma `Base`. Regra **tudo ou nada** (RF002, RNF001, CT-07) | Usa Commons CSV com separador `;` e cabeçalho obrigatório (tolera BOM, espaços e maiúsculas no cabeçalho). Cada erro vira um `ErroValidacao(arquivo, linha, campo, mensagem)`. Se houver **qualquer** erro, devolve `base = null` e o relatório; nada é aplicado. Números aceitam vírgula ou ponto (`numero()`); booleanos aceitam S/N/sim/não/true/false. Erros verificados: arquivo ausente, coluna faltando, código duplicado, tipo desconhecido, pai inexistente ou que não é grupo, ciclo na hierarquia, grupo misturando tipos, valor em grupo, conta inexistente, mês inválido, valor negativo ou não numérico, **REALIZADO depois da data de corte**, lançamento duplicado, percentual ≤ −100 ou ≥ 1000, vínculo para conta ou premissa inexistente. **Avisos** (não impedem a carga): grupo sem filhas, mês fechado sem realizado (vira 0), premissa com nomes diferentes. Limita a 500 erros |
| `ValidacaoEstrutura.java` | Regras de estrutura **compartilhadas** entre a importação e o cadastro pela API | `problemaDoPai()` (pai existe, é grupo e não há ciclo), `tiposFolhaPorGrupo()` (tipos das folhas abaixo de cada grupo, para impedir mistura) e `problemaPercentual()` |
| `ErroValidacao.java`, `RelatorioImportacao.java` | Formato do relatório devolvido pela importação | `valido`, `aplicado`, `erros`, `avisos`, `resumo` (contagens e data de corte) |
| `CsvEscritor.java` | Faz o caminho inverso: `Base` → 5 textos CSV | Mesmo formato que o leitor aceita, com decimal em vírgula. `campo()` põe aspas quando o texto tem `;`. É usado para gravar a base em disco e para o download "baixar arquivos da base". Há um teste que confere ida e volta (`idaEVolta`) |
| `RepositorioBase.java` | Guarda **a base atual** e a grava em disco | Na inicialização: se existir `dados-runtime/base/config.csv` válido, carrega dele (fonte `GRAVADA`); senão, carrega a base fictícia de `resources/dados` (fonte `SEMENTE`). Se a semente for inválida, o programa nem sobe. Guarda a base num `AtomicReference`. `substituir()` grava os 5 CSV (escreve em `.tmp` e depois faz um *move* atômico, para não deixar arquivo pela metade) e troca a referência. `restaurarSemente()` volta à base do projeto |
| `RepositorioCenarios.java` | Guarda os cenários em memória e em `dados-runtime/cenarios.json` | `ConcurrentHashMap` em memória; grava o JSON inteiro a cada mudança (também com `.tmp` + *move*). O cenário `original` não é gravado: é sempre recriado. No JSON, os meses são texto (`"2026-10"`), para não depender de como o Jackson trata chaves de mapa. Se o arquivo estiver corrompido, registra um aviso no log e começa sem cenários |

### 4.6 Serviços (`servico/`)

Ficam entre a API e os repositórios ou o motor. Aqui estão as **regras de uso** do sistema.

| Arquivo | Para que serve | Como foi feito |
|---|---|---|
| `ServicoBase.java` | Tudo sobre a **base**: metadados, estrutura da DRE, cadastro e remoção de contas, premissas, vínculos, importação, exportação e restauração | Os métodos que alteram a base são `synchronized` (um por vez), porque leem a base atual, criam uma nova e substituem. Regras: conta duplicada → 409; estrutura inválida → 422; remover conta com filhas ou com valores → 409 (só se remove por importação); vínculo só em conta folha; premissa com percentuais inválidos → 422 com a lista. `meta()` monta o aviso "DADOS FICTÍCIOS" quando a origem é `FICTICIO`. `importar(arquivos, simular)` valida e só substitui se for válido **e** `simular=false` |
| `ServicoCenario.java` | Tudo sobre **cenários** e **cálculos** | CRUD de cenário: id `cen-` + 8 caracteres aleatórios; nome único sem diferenciar maiúsculas; `copiarDe` copia ajustes. `editavel()` barra o original (409). `ajustarPremissa()` valida **todos** os meses antes de gravar qualquer um (mês fechado, ano sem dados, percentual fora da faixa, mês repetido). `premissasDoCenario()` monta "original × cenário" mês a mês. `dre()` e `comparativo()` chamam o motor; o comparativo compara o **forecast** linha a linha e calcula a diferença e o %. `desempenho()` roda o motor N vezes (até 200) e mede mínimo, média e máximo para o CT-06 |

### 4.7 API REST (`api/`)

Os controllers são **finos**: recebem a requisição, delegam ao serviço e devolvem a resposta.
A lista completa de rotas, com exemplos, está em [backend/README.md](../backend/README.md).

| Arquivo | Rotas | Observações |
|---|---|---|
| `MetaController` | `GET /api/meta` | Data de corte, anos, origem dos dados, fórmulas em uso |
| `DreController` | `GET /api/dre/estrutura`, `POST /api/dre/contas`, `DELETE /api/dre/contas/{codigo}` | `@Valid` valida o `NovaContaRequest` |
| `PremissaController` | `/api/premissas` (GET, GET por código, POST), `/api/vinculos` (GET, POST, DELETE `?conta=&premissa=`) | — |
| `CenarioController` | `/api/cenarios` (GET, POST), `/{id}` (GET, PATCH, DELETE), `/{id}/premissas` (GET), `/{id}/premissas/{codigo}` (PUT, DELETE), `/{id}/dre?ano=`, `/{id}/comparativo?ano=&base=` | A rota `/dre` devolve o `ResultadoDre` do motor direto como JSON |
| `BaseController` | `POST /api/importacao?simular=`, `GET /api/base/arquivos/{nome}`, `POST /api/base/restaurar` | A importação recebe *multipart* com 5 partes. Responde **200** se a base for válida e **422** se não for, sempre com o relatório |
| `EvidenciaController` | `GET /api/evidencias/indicadores.csv`, `/contas.csv`, `/desempenho` | Os CSVs saem no layout da Planilha Oráculo, com coluna `origem_dados` (RF012) |
| `Csv.java` | Monta uma resposta CSV para download | Acrescenta o BOM UTF-8 (`﻿`) para o Excel abrir os acentos corretamente |
| `ApiException.java` | Erro de negócio com status HTTP e **código estável** | Fábricas: `naoEncontrado` (404), `conflito` (409), `regra` (422) |
| `TratadorErros.java` | Transforma **qualquer** erro em `{"erro","mensagem","detalhes"}` | `@RestControllerAdvice` com um método por tipo de exceção: validação → 400 com a lista de campos; JSON mal formado → 400 (se a causa foi um `Periodo` inválido, mostra a mensagem dele); parâmetro faltando ou de tipo errado → 400; rota inexistente → 404; método errado → 405; upload grande → 413; qualquer outro → 500, registrado no log |
| `dto/*.java` | Formatos de entrada (`...Request`) e saída (`...Resposta`) da API | `records` com anotações de validação (`@NotBlank`, `@NotNull`, `@Size`, `@Pattern`, `@NotEmpty`, `@Valid` em listas) |

**Códigos de erro** que o front pode tratar: `NAO_ENCONTRADO`, `REQUISICAO_INVALIDA`,
`CONTA_DUPLICADA`, `CONTA_COM_FILHAS`, `CONTA_COM_VALORES`, `ESTRUTURA_INVALIDA`,
`CONTA_INEXISTENTE`, `CONTA_GRUPO`, `PREMISSA_DUPLICADA`, `PREMISSA_INVALIDA`,
`PREMISSA_INEXISTENTE`, `VINCULO_DUPLICADO`, `CENARIO_SOMENTE_LEITURA`, `NOME_EM_USO`,
`PERIODO_FECHADO`, `AJUSTE_INVALIDO`, `METODO_NAO_PERMITIDO`, `ARQUIVO_GRANDE`, `ERRO_INTERNO`.

### 4.8 Base fictícia e gerador

- `src/main/resources/dados/*.csv`: a base que vem dentro do programa. São 20 contas
  (14 folhas, 6 grupos), 4 premissas (IPCA, IGPM, REAJ_SAL, TARIFA), 11 vínculos e valores de
  2024 a 2027 em **R$ mil**, com corte em `2026-09`.
  - As contas 3.3, 6.4 e 7.1 ficam **sem vínculo de propósito**, para o CT-04.
- `scripts/gerar_base_ficticia.py`: gera esses CSVs com semente aleatória fixa (sempre os
  mesmos números). Ele aplica crescimento por ano, sazonalidade em algumas contas e desvio de
  −5% a +6% no realizado; as deduções acompanham a receita (PIS/COFINS 9,25% e ISS 2%).
  Para regerar: `python3 scripts/gerar_base_ficticia.py src/main/resources/dados`.

### 4.9 Testes

`./mvnw test` roda **45 testes**; 1 deles (o do Oráculo) fica pulado até a planilha ser entregue.

| Arquivo | O que testa | Como |
|---|---|---|
| `calculo/BaseTeste.java` | (auxiliar) Base mínima conferível à mão | O comentário no topo tem a tabela e as contas esperadas |
| `calculo/CasosDeTesteMotorTest.java` | **CT-01 a CT-06** direto no motor | EBITDA 4.200 e margem 38,89% (CT-01); forecast = realizado + projetado (CT-02); IPCA 10% muda só C1 nos meses abertos, resultando em 315,79 (CT-03); vigência; conta sem vínculo = orçado (CT-04); original inalterado (CT-05); 100 cálculos abaixo de 3 s (CT-06); duas premissas multiplicam |
| `calculo/OraculoTest.java` | Confronto com a **Planilha Oráculo** | Só roda se existir `src/test/resources/oraculo/esperado.csv` (instruções no README dessa pasta). Compara cada valor com **igualdade exata** em 2 casas |
| `dados/CarregadorBaseTest.java` | **CT-07**: cada regra de validação | Pega a base fictícia, acrescenta uma linha inválida e confere o erro. Também confere que a semente é válida e que gravar e ler de volta dá a mesma base |
| `dados/ArquivosSemente.java` | (auxiliar) Lê os CSV da base fictícia | — |
| `api/ApiTest.java` | **Todas as rotas da API**, incluindo erros | `@SpringBootTest` + `MockMvc`, sem servidor de verdade. Usa um diretório temporário para os dados e restaura a base antes de cada teste. Cobre o fluxo completo de cenário, importação inválida (base intacta), importação válida, exportações, erros genéricos e persistência em disco |

---

## 5. Front-end

Pasta `frontend/`. HTML, CSS e JavaScript **puros**, sem build. Gráficos com Chart.js 4.4.3
(via CDN, com verificação de integridade SRI). A base visual veio do front do Kayo
(`Kayoyxz/Porto-Itaqui-v2`).

### 5.1 Estrutura e ordem de carregamento

Os scripts são `<script>` comuns, carregados **nesta ordem** no fim do `index.html`. A ordem
importa, porque cada arquivo usa coisas definidas pelos anteriores.

```
base.js        utilitários + objeto Telas = {} (registro de telas)
api.js         Api.* (chamadas HTTP)
estado.js      Estado (cenário/ano atuais, metadados, cache)
graficos.js    Graficos.triplo()
telas/*.js     cada uma faz Telas.<nome> = { titulo, icone, render() }
main.js        roteador + inicialização (por último)
```

Como não há módulos ES, tudo vira variável global (`$`, `Api`, `Estado`, `Telas`...). É simples
e funciona em qualquer navegador; o custo é ter cuidado com nomes repetidos.

### 5.2 index.html e CSS

| Arquivo | Para que serve | Como foi feito |
|---|---|---|
| `index.html` | Toda a estrutura das telas | Mantém a casca original: topo (`.topbar`), menu (`.sidebar`), título (`.page-header`) e diálogos. No topo ficam o **selo DADOS FICTÍCIOS** e o status do servidor. No cabeçalho de cada página, o **seletor de cenário e exercício** (`#ctxCenario`, `#ctxAno`) e o chip "Fechado até". Cada tela é uma `<section class="view" id="view-NOME">`, mostrada ou escondida pelo roteador. Diálogos: `#nomeDialog` (novo/renomear cenário) e `#confirmDialog` (confirmação, original da equipe) |
| `css/style.css` | Estilos originais da equipe | **Não foi alterado.** Tokens de cor (`--azul`, `--topo`...), 4 temas (`data-theme`), cards, `stat`, tabelas, `dialog`, toast, responsivo e impressão |
| `css/app.css` | Estilos novos do domínio | Cores das perspectivas (`--cor-orcado`, `--cor-realizado`, `--cor-forecast`); selo de dados fictícios; **tabela da DRE** com coluna de conta e cabeçalho fixos (`position: sticky`), fundo cinza para mês fechado e creme para aberto; `.favoravel`/`.desfavoravel`; grade de meses das premissas; lista de cenários; caixa de impacto; relatório de importação; responsivo (≤ 1200, ≤ 850, ≤ 600 px) |

### 5.3 Núcleo

| Arquivo | Para que serve | Como foi feito |
|---|---|---|
| `js/base.js` | Utilitários usados por todas as telas | **Formatação** em pt-BR: `fmtNum`, `fmtValor` (usa a unidade da base, ex.: "R$ 228.435 mil"), `fmtSinal` (+/−), `fmtPct`, `variacaoPct`, `rotuloPeriodo("2026-09")` → `set/2026`. **Cor de desvio**: `contaRedutora(tipo)` e `classeDesvio(dif, redutora)`, porque custo **acima** do orçado é ruim e receita acima é bom. **Segurança**: `escapar()` em todo texto inserido como HTML. **Preferências** (`Prefs`): tema, menu, cenário e ano no `localStorage`, com `try/catch` para navegadores que bloqueiam o armazenamento. **Avisos** (`mostrarAviso`, vermelho com `"erro"`), `confirmar()`, `carregando()` (só aparece após 250 ms, para não piscar) e `baixarArquivo()`. **Menu lateral** e **animação de números** vieram do `script.js` original |
| `js/api.js` | **Único lugar que fala com o servidor** | `API_BASE`: se a página vem da porta 8080, usa o mesmo endereço; senão, `http://localhost:8080/api`; dá para forçar com `?api=...` na URL. `chamarApi()` faz o `fetch`, mostra "Calculando…", atualiza o chip de status, transforma erros HTTP em `ErroApi` (com `status`, `codigo`, `detalhes`) e trata 204, texto e blob. A opção `aceitar: [422]` deixa a importação receber o relatório mesmo quando inválida. O objeto `Api` tem um método por rota |
| `js/estado.js` | Contexto global e cache | `Estado.cenarioId` e `Estado.ano` valem para todas as telas. `carregar()` busca meta e cenários e preenche os seletores (escolhe o ano da data de corte se nada estiver salvo). `dre()` guarda **a Promise** no cache por `cenário|ano`, então duas telas pedindo a mesma DRE fazem uma só requisição. `invalidar()` limpa o cache depois de qualquer alteração. `mudarContexto()` salva a preferência e avisa os "ouvintes" (o `main.js` redesenha a tela atual) |
| `js/graficos.js` | Gráfico **triplo** usado na Visão geral e no Comparativo | Chart.js misto: barras de orçado (cinza-azulado) e realizado (azul, só nos meses fechados) e uma **linha** de forecast laranja, **tracejada nos meses abertos** (`segment.borderDash`). O tooltip personalizado e o posicionador `acimaDasBarras` vieram do código original, adaptados para 3 séries e para mostrar "forecast − orçado" com a cor certa. Guarda a instância por canvas e destrói a anterior antes de redesenhar |
| `js/main.js` | Rotas e inicialização | `rotear()`: lê o `#` da URL, mostra a `<section>` certa, atualiza título, breadcrumb e menu ativo e chama `Telas[nome].render()`. `renderizar()` captura erros: sem conexão mostra a caixa vermelha com instruções; outros erros viram aviso. `iniciar()` aplica o tema, carrega `Estado` e desenha. O botão "Tentar novamente" chama `iniciar()` de novo |

### 5.4 As telas (`js/telas/`)

Cada arquivo registra `Telas.nome = { titulo, icone, render }`. `render()` é `async`: busca os
dados e preenche o HTML. Variáveis e funções internas ficam numa função que se executa sozinha
(`(function () { ... })()`), para não poluir o global.

| Arquivo | Tela | Para que serve | Como foi feito |
|---|---|---|---|
| `inicial.js` | Visão geral | Resumo do exercício: EBITDA forecast e orçado, desvio, margem, gráfico mensal e tabela-resumo | Uma chamada `Estado.dre()`. O card de desvio fica verde ou vermelho conforme o sinal. Margem em pontos percentuais (p.p.) |
| `dre.js` | DRE | Tabela conta × mês em 4 perspectivas: forecast, orçado, realizado e forecast − orçado | Gera o HTML da tabela inteira. `naturezas()` descobre o tipo das folhas de cada grupo para colorir o desvio do grupo. Recuo por `nivel`, selos OPEX e premissa nas folhas, linhas finais de receita líquida, EBITDA e margem a partir de `indicadoresMensais`. "Só grupos" esconde as folhas. Exportar CSV usa `/evidencias/contas.csv`. O valor com 2 casas aparece no `title` (passar o mouse) |
| `comparativo.js` | Comparativo | (1) Painel triplo de qualquer indicador ou conta; (2) desvios por grupo: realizado × orçado até o corte e forecast × orçado no ano, com OPEX destacado; (3) se o cenário não for o original, efeito dele em relação ao original | A seção (3) usa `/comparativo` e lista só as linhas alteradas |
| `cenarios.js` | Premissas e cenários | Criar, renomear, excluir e alternar cenários; editar índices mês a mês; ver o impacto; gerenciar vínculos | Uma grade de 12 meses por premissa: campos **bloqueados** nos meses fechados e no cenário original; "Preencher" copia um valor para todos os meses abertos. "Aplicar e recalcular" envia **só os meses que mudaram** (compara com `data-atual`) e mede o tempo até o impacto aparecer (evidência do CT-06 no front). "Voltar ao original" apaga os ajustes da premissa. Vínculos: formulário (conta folha, premissa, mês) e tabela com remoção. Avisa que o vínculo vale **para todos os cenários** |
| `evidencias.js` | Evidências | Botão que **executa CT-01 a CT-07 contra o servidor** e monta a matriz de evidências (imprimível); downloads em CSV; medição de desempenho | **CT-01** confere a fórmula nos totais; **CT-02** confere forecast = realizado/projetado em todas as células; **CT-03 a CT-05** criam um cenário **temporário**, sobem uma premissa em 2 p.p., comparam com o original e tentam editar o original (espera 409), e no final **apagam o cenário temporário**; **CT-06** usa `/desempenho` e o tempo medido no navegador; **CT-07** baixa os 5 CSV, acrescenta um REALIZADO em mês aberto e envia com `simular=true` (espera recusa e base intacta). A base nunca é alterada |
| `configuracoes.js` | Configurações | Importar base, estrutura da DRE e aparência | Importação: 5 campos de arquivo, "Só validar" (`simular=true`) ou "Importar" (com confirmação); mostra erros e avisos com arquivo, linha e campo. Botões para baixar os CSV atuais como modelo e para restaurar a base fictícia. Estrutura: árvore de contas, cadastro de conta (o OPEX é sugerido pelo tipo) e remoção só quando permitido. Aparência: o seletor de tema original da equipe. Painel lateral com dados da base e regras de cálculo vindas de `/api/meta` |

---

## 6. Decisões de projeto e por quê

| Decisão | Por quê |
|---|---|
| `BigDecimal` em vez de `double` | `double` tem erro de arredondamento (0,1 + 0,2 ≠ 0,3). O CT-01 exige divergência **zero** contra a planilha |
| Arredondar só na saída, com `HALF_UP` | É o que o Excel faz (`ARRED`). Arredondar no meio acumularia diferenças |
| Base e cenário imutáveis | Garante por construção as regras RN003 e RN006 (realizado e orçamento original nunca mudam), sem depender de "lembrar de não alterar" |
| Cenário guarda só índices | O cenário é pequeno, fácil de salvar e sempre coerente com a base atual |
| Recalcular a cada consulta, sem cache no servidor | A base é pequena (≈ 1 ms). Cache traria risco de mostrar número velho |
| Importação tudo ou nada | RNF001: nada inválido entra "pela metade" |
| Motor separado do Spring | Testável com `new MotorCalculo(...)`, sem subir servidor. Trocar o framework não afeta o cálculo |
| Fórmula do EBITDA e regra de projeção isoladas em uma classe cada | As duas ainda dependem de validação da GFin. Mudar = editar um lugar |
| CSV + JSON em disco, sem banco de dados | O PRD exclui infraestrutura; arquivos são fáceis de inspecionar e de a equipe de Dados produzir |
| Front sem framework | Não havia tempo para migrar, e o front da equipe já era HTML/CSS/JS puro |
| Um único processo serve API e front | Um comando para rodar e sem problema de CORS na demonstração |

---

## 7. Onde mexer para…

| Quero… | Arquivo(s) |
|---|---|
| Mudar a fórmula do EBITDA ou da margem | `backend/.../calculo/CalculadoraEbitda.java` → método `calcular` |
| Mudar como se projeta um mês aberto | Crie uma classe que implemente `RegraProjecao` e troque em `config/ConfigWeb.java` (`motorCalculo()`) |
| Trocar a base de dados de demonstração | Substitua os 5 CSV em `backend/src/main/resources/dados/` (ou importe pela tela Configurações) |
| Mudar a data de corte | `config.csv` → `data_corte` |
| Adicionar uma regra de validação de CSV | `dados/CarregadorBase.java` e um teste em `CarregadorBaseTest` |
| Criar uma rota nova | Método no serviço (`servico/`) + método no controller (`api/`) + DTO se precisar + teste no `ApiTest` + uma função em `frontend/js/api.js` |
| Criar uma tela nova | `<section class="view" id="view-x">` e link `data-route="x"` no `index.html`; arquivo `js/telas/x.js` com `Telas.x = {...}`; incluir o `<script>` antes do `main.js` |
| Mudar cores ou estilos | Domínio: `frontend/css/app.css`. Base visual: `style.css` (da equipe) |
| Mudar a porta | `backend/src/main/resources/application.properties` → `server.port` (e `API_BASE` em `frontend/js/api.js` se o front rodar separado) |

---

## 8. Limitações conhecidas e pendências

- **Regra de projeção e fórmula do EBITDA** aguardam validação da GFin (RN001, RN004).
- **Planilha Oráculo** ainda não entregue: o `OraculoTest` está pulado.
- **Back-end do Alexandre** (`Branch-Backend-Alexandre`) ainda precisa ser reconciliado com
  este. Ele tem dados derivados da planilha da GFin: confirmar se são autorizados (RN008) antes de usar.
- **Sem autenticação** (fora do escopo do PRD). Qualquer pessoa com acesso ao endereço pode
  alterar cenários e importar bases.
- **Uma instância só**: os dados ficam em arquivos locais, e duas cópias do servidor não compartilham estado.
- Os vínculos são da **base**, não do cenário: criar ou remover um vínculo afeta todos os cenários.
- A **margem do comparativo** mostra a diferença em p.p., não em %.
- O front depende de internet para carregar Chart.js, Font Awesome e a fonte (CDN).
