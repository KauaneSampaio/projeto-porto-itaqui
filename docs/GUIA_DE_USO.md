# 📖 Guia Completo de Uso da Plataforma — Passo a Passo

> **Plataforma Integrada de Projeção Orçamentária e EBITDA (Porto do Itaqui / EMAP)**  
> Este guia explica em linguagem simples e prática **cada tela**, **cada botão**, **cada funcionalidade** e **o que fazer durante a sua demonstração**.

---

## 📌 Sumário
1. [Conceitos Fundamentais em 2 Minutos](#1-conceitos-fundamentais-em-2-minutos)
2. [Como Iniciar o Sistema](#2-como-iniciar-o-sistema)
3. [Barra Superior e Controles Globais](#3-barra-superior-e-controles-globais)
4. [Tela 1 — Visão Geral (`#inicial`)](#tela-1--visão-geral-inicial)
5. [Tela 2 — DRE (`#dre`)](#tela-2--dre-dre)
6. [Tela 3 — Comparativo (`#comparativo`)](#tela-3--comparativo-comparativo)
7. [Tela 4 — Premissas e Cenários (`#cenarios`)](#tela-4--premissas-e-cenários-cenarios)
8. [Tela 5 — Configurações, Fechamento e Auditoria (`#configuracoes`)](#tela-5--configurações-fechamento-e-auditoria-configuracoes)
9. [Roteiro Recomendado para Apresentação / Demonstração (5 Minutos)](#9-roteiro-recomendado-para-apresentação--demonstração-5-minutos)
10. [Perguntas Frequentes e Resolução de Dúvidas](#10-perguntas-frequentes-e-resolução-de-dúvidas)

---

## 1. Conceitos Fundamentais em 2 Minutos

Antes de usar as telas, entenda estes 4 termos usados em todo o sistema:

* **Orçado (Cinza)**: O orçamento aprovado para o ano. Ele é **fixo e imutável**.
* **Realizado (Azul)**: O que de fato aconteceu contabilisticamente. Só existe até a **data de corte** (set/2026).
* **Projetado (Amarelo/Creme)**: A estimativa para os meses futuros (outubro a dezembro/2026), calculada com base nos índices econômicos.
* **Forecast (Laranja)**: A previsão final do ano inteiro.
  $$\text{Forecast} = \text{Realizado dos meses fechados} + \text{Projetado dos meses abertos}$$
* **EBITDA**: Indicador de geração operacional de caixa:
  $$\text{Receita Líquida} - \text{Custos Operacionais} - \text{Despesas Operacionais}$$
* **Data de Corte (`2026-09`)**: O último mês fechado contábil. Até setembro/2026 são meses com cadeado 🔒 (realizado); de outubro em diante são meses abertos para projeção e simulação.

---

## 2. Como Iniciar o Sistema

No terminal da máquina, execute:

```bash
cd /home/tony/nexus/projeto-porto-itaqui/backend
./mvnw spring-boot:run
```

Quando aparecer no terminal:
```text
Tomcat started on port 8080 (http)
Started Aplicacao in ... seconds
```

Abra seu navegador em: **`http://localhost:8080`**

*(Para parar o servidor no terminal, basta pressionar `Ctrl + C`)*.

---

## 3. Barra Superior e Controles Globais

No topo da página, você encontra controles que afetam **todas as telas**:

| Elemento | O que faz / O que significa |
|---|---|
| **Botão Menu (☰)** | Recolhe ou expande a barra lateral esquerda (útil para ganhar espaço na tela da DRE). |
| **Selo "DADOS FICTÍCIOS"** | Selo obrigatório de demonstração (exigência do desafio para deixar claro que os dados são para simulação). |
| **Status do Servidor** | Bolinha verde = *Servidor conectado* (tudo funcionando). Se ficar vermelha, o servidor foi desligado. |
| **Seletor de Cenário** | Escolhe se você está vendo o **"Orçamento Original"** ou um **Cenário Simulado** (ex: *Inflação Alta*, *Reajuste Salarial 8%*). |
| **Seletor de Exercício (Ano)** | Alterna entre **2024**, **2025**, **2026** e **2027**. |
| **Chip "Fechado até"** | Mostra `set/2026` — lembra você e a banca até onde os números são fato consumado e a partir de onde são projeção. |

---

## Tela 1 — Visão Geral (`#inicial`)

> **Objetivo**: Dar ao gestor um resumo executivo imediato da saúde financeira da EMAP no ano selecionado.

### O que você vê nesta tela:
1. **Quatro Cards Principais no Topo**:
   * **EBITDA Forecast**: O resultado projetado para o fechamento do ano.
   * **EBITDA Orçado**: A meta aprovada no orçamento.
   * **Desvio**: Diferença (Forecast − Orçado). Se estiver **verde**, o resultado está acima da meta (favorável); se **vermelho**, abaixo (desfavorável).
   * **Margem EBITDA**: O percentual da Receita Líquida que virou EBITDA (ex: 30,4%).
2. **Gráfico Triplo Mensal (Chart.js)**:
   * **Barras cinzas**: Orçado mês a mês.
   * **Barras azuis**: Realizado nos meses que já fecharam (jan a set).
   * **Linha laranja**: Forecast (repare que ela fica **tracejada** a partir de outubro, indicando que são projeções futuras).
3. **Tabela Resumo**: Visão sintética consolidada das grandes linhas da DRE no ano.

### O que fazer na demonstração:
* Mostre como a linha do gráfico conecta o passado real (azul) com a estimativa futura (tracejado).
* Alterne o ano no cabeçalho (de 2026 para 2025 ou 2024) para mostrar que a plataforma armazena histórico contábil completo.

---

## Tela 2 — DRE (`#dre`)

> **Objetivo**: Demonstrar o resultado detalhado em formato de Demonstração de Resultado do Exercício com todas as 53 contas oficiais da EMAP.

### Controles disponíveis:
* **Filtro de Perspectiva**:
  * `Forecast`: Exibe a composição oficial (Realizado no fechado + Projetado no aberto).
  * `Orçado`: Mostra apenas as metas orçadas.
  * `Realizado`: Mostra apenas o que de fato aconteceu (meses abertos ficam com "—").
  * `Desvio`: Mostra se cada conta gastou mais ou menos do que o previsto.
* **Checkbox "Só grupos"**: Esconde as contas detalhadas e deixa apenas os grupos principais (ótimo para visão executiva rápida).
* **Botão "Exportar CSV"**: Baixa a DRE completa para abrir no Excel.
* **Botão "Imprimir"**: Gera versão limpa pronta para PDF ou impressão.

### Como ler a tabela:
* As colunas de **jan a set** têm fundo acinzentado e um ícone de **cadeado 🔒**: são dados fechados e inalteráveis.
* As colunas de **out, nov e dez** têm fundo creme claro e o rótulo **"proj."**: são projeções dinâmicas.
* Passe o mouse em qualquer número para ver o valor com 2 casas decimais exatas.
* Selo **OPEX**: Identifica visualmente contas de despesa e custo operacional.
* Selo de Premissa (ex: `INPC`, `IGP-M`): Mostra qual índice econômico governa aquela conta.

---

## Tela 3 — Comparativo (`#comparativo`)

> **Objetivo**: Comparar o desempenho entre perspectivas e analisar contas individualmente.

### Recursos:
1. **Seletor de Indicador / Conta**:
   * Permite escolher qualquer indicador geral (EBITDA, Receita Líquida, Custos) ou qualquer conta específica (ex: *C. Pessoal e Encargos*, *Tarifas portuárias*).
   * O gráfico triplo abaixo se redesenha instantaneamente para mostrar o comportamento daquela conta específica.
2. **Painel de Desvios por Grupo**:
   * Mostra duas comparações cruciais:
     1. *Realizado × Orçado até o corte* (como estamos nos saindo até o momento).
     2. *Forecast × Orçado no ano* (como vamos terminar o ano).
   * O sistema inverte as cores com inteligência contábil:
     * Receita maior que o orçado = **Verde** (bom).
     * Custo ou Despesa maior que o orçado = **Vermelho** (ruim).
3. **Efeito do Cenário (quando um cenário simulado estiver ativo)**:
   * Se você estiver visualizando um cenário criado por você, aparece uma tabela exclusiva mostrando exatamente quais linhas sofreram impacto em relação ao orçamento original.

---

## Tela 4 — Premissas e Cenários (`#cenarios`)

> **Objetivo**: A parte mais poderosa da plataforma — simular "e se?" sem alterar a base oficial.

### Como criar e testar um cenário simulado (Passo a Passo):
1. **Criar o Cenário**:
   * Clique no botão **"Novo cenário"** no topo da tela.
   * Dê um nome (exemplo: `Dissídio Coletivo 8%` ou `Inflação Alta 2026`).
   * Clique em **Salvar**.
   * O cenário novo é ativado automaticamente no cabeçalho.
2. **Editar as Premissas**:
   * Abaixo você verá as premissas oficiais cadastradas: **INPC**, **IGP-M** e **IPCA**.
   * Repare que os meses de **jan a set estão bloqueados com cadeado** (não é permitido mudar o passado!).
   * Apenas **out, nov e dez** podem ser alterados.
   * **Dica de agilidade**: Você pode usar o campo *"Valor para todos os meses abertos"*, digitar `8,00` e clicar em **"Preencher"**.
3. **Aplicar e Recalcular**:
   * Clique no botão azul **"Aplicar e recalcular"**.
   * O sistema recalcula todas as 53 contas da DRE no servidor em cerca de 2 milissegundos.
   * Uma caixa de impacto surge na hora informando:
     > *"EBITDA Forecast: R$ 177.396 mil ➔ R$ 174.120 mil (-R$ 3.276 mil) · recalculado em 2 ms"*.
4. **Gerenciar Vínculos Contratuais (abaixo na tela)**:
   * Permite consultar ou cadastrar qual índice governa qual conta e a partir de qual mês.
5. **Proteger o Orçamento Original**:
   * Se você selecionar o cenário *"Orçamento original"*, todos os campos ficam bloqueados contra edição (erro 409 se alguém tentar forçar), garantindo conformidade com a regra de negócio da EMAP.

---

## Tela 5 — Configurações, Fechamento e Auditoria (`#configuracoes`)

> **Objetivo**: Gestão operacional da plataforma — Lançamento do realizado mês a mês, matriz de evidências, importação em lote, plano de contas, logs de auditoria e personalização visual.

### Sub-Abas disponíveis:

#### 1. 📅 Fechamento Mensal (Lançamento Simplificado e Intuitivo)
* **Para que serve**: É o canal oficial para o gestor financeiro lançar os números contábeis reais do mês que acabou de fechar sem precisar manipular CSVs.
* **Como usar**:
  1. No campo **"Mês a fechar (Período)"**, selecione o mês seguinte (o sistema já sugere automaticamente o próximo mês em relação ao corte atual, ex: `2026-10`).
  2. A tabela abaixo lista todas as contas analíticas (Movimentação TEGRAM, Granéis Líquidos, Pessoal, Custos, etc.).
  3. Você pode usar a barra de busca para encontrar rapidamente uma conta específica.
  4. **Dica de agilidade**: Você pode clicar no botão **"Preencher com Orçado"** para carregar os valores orçados previstos para o mês como base e depois apenas ajustar as contas que divergiram!
  5. Clique em **"Confirmar Fechamento do Mês"**.
  6. **Efeito**: A data de corte avança (ex: para `out/2026`), o mês passa a ser tratado como **fechado/realizado**, o novo Forecast é recalculado na hora e a operação é gravada com carimbo de tempo no **Log de Auditoria**.

#### 2. 📋 Evidências (CT-01 a CT-07)
*(Anteriormente uma tela separada, agora unificada na área de governança/configurações).*
* Clique no botão **"Executar verificação"** para rodar os 7 Casos de Teste formais da avaliação:
  * **CT-01**: Validação das fórmulas de EBITDA e Margem.
  * **CT-02**: Forecast = Realizado nos meses fechados + Projetado nos abertos.
  * **CT-03**: Prova de recalculo isolado por premissa vinculada.
  * **CT-04**: Contas sem premissa mantêm projeção original.
  * **CT-05**: Proteção do orçamento original (impossível alterar o oficial).
  * **CT-06**: Desempenho e velocidade (recálculo em menos de 3.000 ms).
  * **CT-07**: Integridade e recusa total de carga inconsistente.
* Botão **"Imprimir"** para gerar a folha formal da matriz de testes.
* Botões de exportação para conferência com a Planilha Oráculo.

#### 3. 🕒 Logs de Auditoria (Rastreabilidade)
* **Para que serve**: Histórico transparente de todas as operações administrativas e de governança realizadas no sistema.
* **O que registra**:
  * Criação, renomeação e exclusão de cenários.
  * Alterações de premissas e simulações.
  * Fechamento mensal de realizado (com identificação do período e quantidade de contas).
* **Preparado para Autenticação**: Cada registro já armazena a coluna `Usuário` (atualmente com identificação do perfil operacional, pronto para se plugar diretamente ao futuro módulo de login/autenticação).

#### 4. 📥 Importar Base (CSV em lote)
* Importação massiva via 5 arquivos CSV (`config.csv`, `contas.csv`, `valores.csv`, `premissas.csv`, `vinculos.csv`).
* Validação atômica "tudo ou nada" e botão de **Restaurar base fictícia**.

#### 5. 🗂 Estrutura da DRE
* Consulta a hierarquia do plano de contas e cadastro de novas contas.

#### 6. 🎨 Aparência
* Alternância de temas visuais (Azul padrão, Verde porto, Grafite e Índigo).

---

## 10. Roteiro Recomendado para Apresentação / Demonstração (5 Minutos)

Se você tiver apenas alguns minutos para apresentar para os avaliadores, siga este roteiro infalível:

1. **Minuto 1 — Visão Geral e Governança**:
   * Abra em `http://localhost:8080/#inicial`.
   * Aponte o selo **"DADOS FICTÍCIOS — DEMONSTRAÇÃO"** e o chip **"Fechado até set/2026"**.
   * Destaque os cards de EBITDA e o gráfico triplo mostrando como o passado realizado (barras azuis) se conecta à linha tracejada de previsão (forecast).
2. **Minuto 2 — A DRE Contábil**:
   * Vá para o menu **DRE (`#dre`)**.
   * Mostre o detalhamento das operações reais do Porto do Itaqui (TEGRAM, Granéis Líquidos, VLI, Suzano, etc.).
   * Mostre os cadeados 🔒 nos meses fechados (jan a set) e o fundo diferenciado nos meses projetados (out a dez).
3. **Minuto 3 — Simulação de Cenários ao Vivo**:
   * Vá para o menu **Premissas e cenários (`#cenarios`)**.
   * Mostre que no *Orçamento original* os campos estão bloqueados.
   * Clique em **"Novo cenário"**, dê o nome de *"Simulação Dissídio 8%"*.
   * Na premissa **INPC**, preencha os meses abertos com `8,00` e clique em **"Aplicar e recalcular"**.
   * Aponte o recálculo instantâneo (2 ms) e a caixa que mostra a perda/ganho de EBITDA em relação ao orçamento original.
4. **Minuto 4 — Comparativo**:
   * Vá para o menu **Comparativo (`#comparativo`)**.
   * Mostre na tabela inferior *"Efeito deste cenário em relação ao original"* que **apenas** as contas vinculadas a pessoal sofreram alteração, provando a rastreabilidade do motor de cálculo.
5. **Minuto 5 — Auditoria e Prova Técnica**:
   * Vá para o menu **Evidências (`#evidencias`)**.
   * Clique em **"Executar verificação"**.
   * Mostre os 7 Casos de Teste (CT-01 a CT-07) sendo aprovados na hora com tempo de resposta milimétrico.
   * Finalize mostrando que a aplicação é um artefato único, executável e seguro.

---

## 11. Perguntas Frequentes e Resolução de Dúvidas

* **"Se eu alterar um número no cenário simulado, posso estragar o orçamento oficial?"**
  * **Não.** A arquitetura foi construída com objetos imutáveis. O orçamento oficial é somente leitura. Um cenário armazena apenas a variação do índice.
* **"Posso alterar o realizado do mês de março que já fechou?"**
  * **Não.** O sistema bloqueia ajustes em períodos com data menor ou igual à data de corte (`PERIODO_FECHADO`), garantindo que o passado contábil seja inviolável.
* **"Onde os dados ficam salvos se não tem banco de dados?"**
  * A base padrão fica nos CSVs embutidos no sistema. Cenários criados pelo usuário são gravados automaticamente na pasta `backend/dados-runtime/cenarios.json`.
* **"Fiz muitos testes e quero limpar tudo para a apresentação:"**
  * Vá na tela **Configurações ➔ Restaurar base**, ou no terminal pare o servidor e apague a pasta temporária:
    ```bash
    rm -rf /home/tony/nexus/projeto-porto-itaqui/backend/dados-runtime
    ```
    Ao reiniciar, o sistema sobe no estado limpo e perfeito de fábrica.
