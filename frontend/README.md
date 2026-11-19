# Front-end — Projeção Orçamentária e EBITDA

HTML, CSS e JavaScript puro, com Chart.js. Sem build e sem framework. Os dados vêm da API do
back-end (`backend/`). Não há mais nada guardado no `localStorage` além de preferências
(tema, menu, cenário e exercício escolhidos).

Base visual: casca, `css/style.css` e utilitários da versão anterior da equipe
(`Porto-Itaqui-v2/`). Essa pasta antiga não foi alterada.

## Rodar

O jeito mais simples é deixar o back-end servir esta pasta:

```bash
cd backend && ./mvnw spring-boot:run
```

Abra `http://localhost:8080`.

Também funciona com Live Server ou qualquer servidor estático: a API é procurada em
`http://localhost:8080/api` (o CORS já libera `localhost` e `127.0.0.1`). Para outro endereço,
use `?api=http://endereco:porta/api` na URL.

Abrir o `index.html` direto do disco (`file://`) **não funciona**, porque o navegador bloqueia
as chamadas à API.

## Arquivos

```
index.html            casca (topo, menu, telas, diálogos)
css/style.css         estilos originais da equipe (sem alterações)
css/app.css           estilos novos: DRE, cenários, selo de dados fictícios
js/base.js            utilitários: formatação, avisos, confirmação, tema, menu
js/api.js             ÚNICO lugar que chama o back-end
js/estado.js          cenário e exercício selecionados, metadados, cache de cálculo
js/graficos.js        gráfico triplo orçado × realizado × forecast
js/telas/*.js         uma tela por arquivo
js/main.js            rotas (#inicial, #dre...) e inicialização
```

Os scripts são carregados em ordem por `<script>` (sem módulos ES).

## Telas

| Rota | Tela | Responsável (PRD) | Requisitos |
|---|---|---|---|
| `#inicial` | Visão geral: EBITDA forecast × orçado, margem, gráfico mensal | Izabelle | RF005, RF006 |
| `#dre` | DRE conta × mês nas perspectivas forecast, orçado, realizado e desvio | Izabelle / Hugo | RF001, RF006–RF008 |
| `#comparativo` | Painel triplo por indicador/conta, desvios por grupo com OPEX, cenário × original | Hugo | RF006, RF009, RF010 |
| `#cenarios` | Criar/alternar cenários, editar índices dos meses abertos, vínculos conta → premissa | Samuel | RF003, RF004, RF009 |
| `#evidencias` | Verificação automática CT-01 a CT-07, exportação CSV, desempenho | Ryan | RF011, CT-01..07 |
| `#configuracoes` | Importar base (com relatório de erros), estrutura da DRE, aparência | Kayo | RF001, RF002, CT-07 |

Transversal (Kayo): selo **DADOS FICTÍCIOS** no topo (RF012), indicador "Calculando…",
status do servidor, aviso com instruções quando o servidor está desligado, validação dos
campos antes de enviar e mensagens de erro da API em aviso vermelho.

## Regras visíveis na interface

- Cenário e exercício ficam no cabeçalho e valem para todas as telas.
- Meses fechados aparecem com cadeado e fundo cinza; os abertos, com fundo creme e o rótulo "projetado".
- O orçamento original é somente leitura: os campos ficam bloqueados.
- Nos cenários, só os meses abertos podem ser editados.
- Desvio em verde é favorável ao EBITDA e em vermelho é desfavorável. A cor considera a natureza
  da conta: custo acima do orçado é vermelho.
- Valores na unidade da base (hoje R$ mil). Nas tabelas, sem casas decimais; passe o mouse sobre
  uma célula para ver as 2 casas.

## Verificação automática (tela Evidências)

O botão **Executar verificação** roda os casos de teste contra o servidor:

- cria um cenário temporário, altera uma premissa e confere CT-03, CT-04 e CT-05;
- simula uma importação inválida, só validando, para o CT-07;
- mede o tempo para o CT-06;
- apaga o cenário temporário no final.

A base não é alterada. O resultado pode ser impresso como matriz de evidências.
