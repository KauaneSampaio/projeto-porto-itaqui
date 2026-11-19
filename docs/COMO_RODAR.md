# Como rodar o projeto

Guia passo a passo para colocar a plataforma no ar, rodar os testes e resolver os problemas
mais comuns. Para entender o código, veja [GUIA_DO_CODIGO.md](GUIA_DO_CODIGO.md).

---

## 1. O que você precisa

| Item | Versão | Para quê |
|---|---|---|
| **JDK** (Java Development Kit) | **21** | Compilar e rodar o back-end. O JRE sozinho **não serve**: ele não tem o compilador `javac` |
| Internet | — | Na primeira vez, o Maven baixa as bibliotecas (≈ 30 MB). O front carrega Chart.js e os ícones da internet |
| Git | qualquer | Baixar o código |
| Navegador | Chrome, Edge ou Firefox atuais | Usar a interface |

**Não precisa** instalar Maven, Node, banco de dados nem servidor web.

### 1.1 Conferir se o JDK já está instalado

```bash
javac -version
```

Se aparecer `javac 21...`, está pronto. Se aparecer "comando não encontrado" ou outra versão,
instale o JDK.

### 1.2 Instalar o JDK 21

**Ubuntu / Linux Mint / Pop!_OS**

```bash
sudo apt update && sudo apt install -y openjdk-21-jdk-headless
```

**Windows**: baixe e instale o "Temurin 21 (LTS)" em <https://adoptium.net>. Na instalação, marque
**"Set JAVA_HOME variable"** e **"Add to PATH"**. Feche e abra o terminal depois.

**macOS** (com Homebrew)

```bash
brew install --cask temurin@21
```

Confira de novo com `javac -version`.

---

## 2. Baixar o código

Se ainda não tem o repositório:

```bash
git clone git@github.com:KauaneSampaio/projeto-porto-itaqui.git
```

```bash
cd projeto-porto-itaqui && git switch Branch-Plataforma-Antony
```

> A branch só existe no GitHub depois que alguém fizer o `git push`. Antes disso, use a cópia
> local em `/home/tony/nexus/projeto-porto-itaqui`.

Estrutura:

```
projeto-porto-itaqui/
├── backend/      ← API e motor de cálculo (é aqui que você roda os comandos)
├── frontend/     ← interface (servida pelo back-end)
├── docs/         ← este guia e o guia do código
└── ARQUITETURA.md
```

---

## 3. Rodar a aplicação (jeito recomendado)

Entre na pasta do back-end:

```bash
cd backend
```

**Linux / macOS**

```bash
./mvnw spring-boot:run
```

**Windows (PowerShell ou CMD)**

```
mvnw.cmd spring-boot:run
```

Na primeira vez demora um pouco, porque o Maven é baixado. Espere aparecer uma linha parecida com:

```
Tomcat started on port 8080 (http)
Started Aplicacao in 2.1 seconds
```

Abra no navegador: **<http://localhost:8080>**

O que você deve ver:
- o selo amarelo **DADOS FICTÍCIOS** e "Servidor conectado" no topo;
- a Visão geral com o EBITDA de 2026.

Para parar o servidor: **Ctrl + C** no terminal.

> O back-end serve o front-end automaticamente (a pasta `../frontend`). Por isso é preciso rodar
> o comando **de dentro de `backend/`**.

---

## 4. Rodar os testes

Dentro de `backend/`:

```bash
./mvnw test
```

Resultado esperado (no fim da saída):

```
Tests run: 45, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

O teste pulado é o `OraculoTest`. Ele só roda quando a Planilha Oráculo for colocada em
`backend/src/test/resources/oraculo/esperado.csv` (instruções no README dessa pasta).

Para rodar uma classe de teste só:

```bash
./mvnw test -Dtest=CasosDeTesteMotorTest
```

Se trocou arquivos de teste e algo parece "velho", limpe antes:

```bash
./mvnw clean test
```

Também dá para rodar a verificação **pela interface**: menu **Evidências → Executar
verificação**. Ela testa CT-01 a CT-07 contra o servidor rodando e não altera a base.

---

## 5. Gerar um único arquivo para a demonstração

Para levar a aplicação para outro computador (que também precisa de Java 21):

```bash
./mvnw clean package
```

Isso cria `backend/target/itaqui-ebitda-0.1.0.jar`. Para rodar:

```bash
java -jar target/itaqui-ebitda-0.1.0.jar
```

> Atenção: rodado assim de dentro de `backend/`, ele encontra o front em `../frontend`. Se copiar
> o `.jar` para outro lugar, copie a pasta `frontend/` junto, mantendo a mesma posição relativa
> (o `.jar` dentro de uma pasta irmã de `frontend/`).

---

## 6. Rodar o front-end separado (opcional)

Útil para quem mexe só na interface com o **Live Server** do VS Code.

1. Deixe o back-end rodando (seção 3).
2. Abra a pasta `frontend/` no VS Code e clique em **Go Live**.
3. A página abre em `http://127.0.0.1:5500` e chama a API em `http://localhost:8080/api`
   automaticamente. O CORS já está liberado para `localhost` e `127.0.0.1`.

Para apontar para outro servidor, acrescente `?api=` na URL:

```
http://127.0.0.1:5500/?api=http://192.168.0.10:8080/api
```

> Abrir o `index.html` com dois cliques (endereço `file://...`) **não funciona**: o navegador
> bloqueia as chamadas à API. Use o back-end ou um servidor como o Live Server.

---

## 7. Dados: onde ficam e como reiniciar

| O quê | Onde |
|---|---|
| Base fictícia original (vem no código) | `backend/src/main/resources/dados/*.csv` |
| Base alterada ou importada e cenários criados | `backend/dados-runtime/` (criada ao rodar; fora do git) |

Para **voltar ao estado inicial**, use uma destas opções:
- tela **Configurações → Importar base → Restaurar** (mantém os cenários);
- ou pare o servidor e apague a pasta de dados (apaga também os cenários):

```bash
rm -rf dados-runtime
```

**Importar outra base** (por exemplo, a da equipe de Dados): tela **Configurações → Importar base**,
selecione os 5 CSV e clique em "Só validar" para conferir; depois, em "Importar". O formato dos
arquivos está em [backend/README.md](../backend/README.md#formato-dos-csvs-equipe-de-dados).
Também dá para usar o terminal (com o servidor rodando, de dentro da pasta dos CSV):

```bash
curl -F config=@config.csv -F contas=@contas.csv -F valores=@valores.csv -F premissas=@premissas.csv -F vinculos=@vinculos.csv "http://localhost:8080/api/importacao?simular=true"
```

Troque `simular=true` por `simular=false` para aplicar de verdade.

---

## 8. Testar a API sem a interface

Com o servidor rodando:

```bash
curl -s http://localhost:8080/api/meta
```

```bash
curl -s "http://localhost:8080/api/cenarios/original/dre?ano=2026"
```

Lista completa de rotas: [backend/README.md](../backend/README.md#endpoints).

---

## 9. Problemas comuns

| Sintoma | Causa | Solução |
|---|---|---|
| `release version 21 not supported` ou `No compiler is provided` | Só o JRE está instalado, ou o `JAVA_HOME` aponta para outro Java | Instale o **JDK** 21 (seção 1.2). Confira `javac -version`. Se tiver vários Javas, defina `JAVA_HOME` (ex.: `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`) |
| `./mvnw: Permission denied` | O arquivo perdeu a permissão de execução | `chmod +x mvnw` |
| `Port 8080 was already in use` | Outro programa (ou outra cópia do servidor) usa a porta | Feche o outro processo, ou rode em outra porta: `./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081` (e abra `localhost:8081`) |
| Erro ao baixar dependências (`Could not transfer artifact`) | Sem internet, ou proxy da rede | Conecte-se a outra rede. Na primeira vez a internet é obrigatória |
| Página abre, mas mostra "Não foi possível falar com o servidor" | O back-end não está rodando ou está em outro endereço | Rode a seção 3; confira o endereço exibido na caixa vermelha; clique em "Tentar novamente" |
| Página em branco ou sem estilos | Aberta por `file://` ou sem internet (CDN) | Abra por `http://localhost:8080`. Os ícones e gráficos vêm da internet |
| Página abre, mas fica sem a interface (erro 404 em `/`) | O servidor foi iniciado fora da pasta `backend/` | Pare e rode de novo **dentro** de `backend/` |
| Números estranhos depois de vários testes manuais | Base editada ou importada | Configurações → Restaurar, ou apague `dados-runtime/` |
| `OraculoTest` falha "do nada" | Arquivo antigo em `target/` | `./mvnw clean test` |
| Aviso "Base gravada … é inválida" no log | O arquivo em `dados-runtime/base/` foi editado à mão com erro | O sistema já volta para a base fictícia sozinho. Apague `dados-runtime/base/` para sumir o aviso |

---

## 10. Roteiro rápido para a demonstração (≈ 5 min)

1. **Visão geral**: apresente o selo "dados fictícios", o EBITDA forecast × orçado, a margem e o gráfico (barras = orçado e realizado; linha tracejada = projeção).
2. **DRE**: mostre os meses com cadeado (fechados) e os projetados; alterne entre Forecast, Orçado, Realizado e Desvio; mostre as linhas de EBITDA e margem no fim.
3. **Premissas e cenários**:
   - mostre que o "Orçamento original" está bloqueado;
   - clique em **Novo** e dê um nome (ex.: "Reajuste salarial 8%");
   - abra `REAJ_SAL`, digite 8 em "Valor para todos os meses abertos" e clique em **Preencher** e depois em **Aplicar e recalcular**;
   - mostre o impacto no EBITDA e o tempo de recálculo.
4. **Comparativo**: com o cenário selecionado, mostre "cenário × original" e as contas que mudaram (só as de pessoal); OPEX destacado.
5. **Evidências**: clique em **Executar verificação** → CT-01 a CT-07 aprovados; **Imprimir** gera a matriz de evidências.
6. **Configurações**: mostre que uma importação com erro é recusada com a linha do problema e não altera nada.

Antes de apresentar: **Configurações → Restaurar** e apague cenários de teste, para começar limpo.
