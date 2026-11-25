# PROJECT_CONTEXT.md — Ebitify

> Documento de continuidade técnica atualizado em 24/11/2026.  
> **Não inclui credenciais, senhas ou segredos.**

---

## A. Visão Geral

| Campo | Valor |
|---|---|
| **Nome do Produto** | **Ebitify** — Plataforma de Projeção Orçamentária e EBITDA |
| **Repositório** | `projeto-porto-itaqui` — branch ativa: `Branch-Plataforma-Antony` |
| **Contexto** | Desafio Jovem Tech 2026 — Porto do Itaqui / EMAP |
| **Objetivo** | Plataforma web segura para visualizar a DRE (Orçado × Realizado × Forecast), calcular EBITDA e Margem, simular cenários por índices econômicos, lançar fechamentos mensais e auditar ações por usuário |
| **Dados** | Base fictícia estruturada sobre o plano de contas oficial (53 contas, 2024–2027, corte `2026-09`) |

---

## B. Stack Tecnológica

### Back-end
- **Java 21 (LTS)** + **Spring Boot 3.4.5** (`spring-boot-starter-web`, `spring-boot-starter-validation`)
- **Apache Commons CSV 1.12.0** (leitura/escrita de CSV)
- **BigDecimal** em toda aritmética financeira (arredondamento `HALF_UP` na saída)
- **Segurança nativa Java 21**: `PBKDF2WithHmacSHA256` (210.000 iterações + salt aleatório + comparação em tempo constante), `HttpSession` com cookie `HttpOnly` + `SameSite=Lax`, proteção CSRF (`X-CSRF-Token`), rate limit por IP+login e cabeçalhos de segurança HTTP
- **JUnit 5 + AssertJ + MockMvc** (**49 testes passando**)

### Front-end
- **HTML / CSS / JS puro** (sem build), servido diretamente pelo Spring Boot (`file:../frontend/`)
- **Chart.js 4.4.3**, **Font Awesome 6.5.2** e **Google Fonts (Public Sans)** via CDN
- **Tela de login** dedicada (`login.html`, `css/login.css`, `js/login.js`)

### Persistência (em arquivos locais, sem banco externo)
- `backend/src/main/resources/dados/*.csv` — base fictícia padrão (53 contas, 3.348 valores 2024–2027, 3 índices: INPC, IGP-M, IPCA, 19 vínculos)
- `backend/dados-runtime/base/*.csv` — base ativa editada/importada
- `backend/dados-runtime/cenarios.json` — cenários simulados persistidos
- `backend/dados-runtime/logs-auditoria.json` — histórico de auditoria
- `backend/dados-runtime/usuarios.json` — contas de usuário (senhas apenas em hash PBKDF2)

---

## C. Arquitetura e Módulos

```text
backend/src/main/java/br/jovemtech/itaqui/
├── api/         Controllers REST (Meta, Dre, Premissa, Cenario, Base, Evidencia) + DTOs + TratadorErros
├── calculo/     Motor de cálculo Java puro (MotorCalculo, MotorForecast, AgregadorDre, CalculadoraEbitda, ProjecaoPorIndice)
├── config/      ConfigWeb (CORS + bean do motor), PropriedadesApp (dados, cors, auth)
├── dados/       CarregadorBase, CsvEscritor, ValidacaoEstrutura, RepositorioBase, RepositorioCenarios, RepositorioLogs
├── dominio/     Records imutáveis (Base, Conta, Periodo, Premissa, Cenario, Vinculo, ValorLancado, RegistroLog)
├── seguranca/   FiltroAutenticacao, AuthController, RepositorioUsuarios, Usuario, HashSenha, LimitadorTentativas, ContextoUsuario
└── servico/     ServicoBase, ServicoCenario
```

### Princípios preservados
1. **Motor de cálculo sem Spring** (`MotorCalculo` recebe `Base` + `Cenario` e retorna `ResultadoDre`).
2. **Imutabilidade**: `Base` e `Cenario` são imutáveis; alterações geram novas instâncias.
3. **Bloqueio de segurança no servidor**: `FiltroAutenticacao` bloqueia `/api/**` sem sessão (`401`), redireciona páginas internas para `/login.html` (`302`), exige `X-CSRF-Token` em mutações (`403`) e restringe `/api/evidencias/**` e `/api/auth/usuarios/**` ao perfil `ADMIN` (`403`).
4. **Rastreabilidade real**: `ContextoUsuario` propaga o login da sessão para todos os logs gravados por `ServicoBase`, `ServicoCenario` e `AuthController`.

---

## D. Funcionalidades Implementadas

### Painel Financeiro
- **Visão Geral (`#inicial`)**: cards de EBITDA Forecast, Orçado, Desvio e Margem + gráfico triplo mensal + tabela resumo. O chip *Fechado até* oculta-se automaticamente em anos futuros (ex.: 2027).
- **DRE (`#dre`)**: 53 contas em árvore, 4 perspectivas (Forecast, Orçado, Realizado, Desvio), destaque de OPEX e índices vinculados, exportação CSV e impressão.
- **Comparativo (`#comparativo`)**: painel triplo por indicador/conta, desvios por grupo e comparativo cenário × orçamento oficial.
- **Premissas e Cenários (`#cenarios`)**:
  - Fluxo guiado em 3 passos (Escolher/Criar → Ajustar índices dos meses abertos → Ver impacto no EBITDA).
  - Atalhos (`+0,5 p.p.`, `−0,5 p.p.`, preenchimento em lote), barra flutuante de alterações pendentes e cards de impacto antes × depois.
  - Liberação explícita e auditada para edição direta do **Orçamento Oficial** (`PUT /api/premissas/{codigo}`), mantendo `PUT /api/cenarios/original/premissas/{codigo}` protegido com `409`.
  - Seção avançada recolhida para gestão de vínculos conta → índice.

### Configurações, Governança e Segurança (`#configuracoes`)
- **Fechamento mensal**: formulário intuitivo para lançar o realizado de um mês (com filtro de contas e botão *Preencher com Orçado*) e avançar a data de corte (`POST /api/base/fechamento`).
- **Importar base (CSV)**: carga atômica "tudo ou nada" dos 5 CSVs + restauração da base padrão.
- **Estrutura da DRE**: visualização da árvore e cadastro/remoção de contas.
- **Logs de auditoria**: histórico de cenários, premissas, fechamento mensal, edição do oficial, logins, logouts, falhas de acesso e gestão de usuários.
- **Aparência**: 4 temas de cores e preferência de menu recolhido.
- **Minha conta**: alteração da própria senha (exigindo senha atual + regra de força mínima).
- **Usuários (Admin)** *(somente `ADMIN`)*: criar contas (`ADMIN` ou `GESTOR`), ativar/desativar e excluir (com proteção contra auto-remoção/auto-bloqueio).
- **Evidências (Admin)** *(somente `ADMIN`)*: execução automática CT-01 a CT-07, medição de desempenho (< 3 s), exportação CSV para a Planilha Oráculo e impressão.

---

## E. Testes Automatizados (49/49 passando)

| Classe | Qtd | O que valida |
|---|---|---|
| `ApiTest` | 17 | Endpoints REST, DRE, cenários, importação, evidências e persistência |
| `SegurancaTest` | 4 | Bloqueio 401/302 sem login, headers de segurança, rate limit 429, CSRF 403, auditoria com usuário real, logout e autorização `ADMIN` vs `GESTOR` |
| `OraculoTest` | 1 | Confronto automático do motor com os 189 valores de `esperado.csv` |
| `CasosDeTesteMotorTest` | 9 | Regras matemáticas CT-01 a CT-06 no motor de cálculo |
| `CarregadorBaseTest` | 18 | Validação atômica de CSVs (CT-07) |

---

## F. Comandos Úteis

```bash
# Definir JDK 21 (se o padrão do sistema for JRE)
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

# Rodar todos os testes
cd /home/tony/nexus/projeto-porto-itaqui/backend && ./mvnw test

# Iniciar a aplicação (porta 8080)
cd /home/tony/nexus/projeto-porto-itaqui/backend && ./mvnw spring-boot:run

# Empacotar JAR para deploy
cd /home/tony/nexus/projeto-porto-itaqui/backend && ./mvnw clean package
```

### Checklist para hospedagem na internet
1. Configurar proxy reverso com HTTPS (Nginx / Caddy / Cloudflare).
2. Definir `APP_COOKIE_SECURE=true`, `APP_CORS_ORIGENS=` (vazio) e senhas fortes via `APP_AUTH_SENHA_ADMIN` / `APP_AUTH_SENHA_GESTOR` (ou trocar em *Configurações → Minha conta*).
3. Manter a pasta `frontend/` acessível ao servidor (ou embutida em `src/main/resources/static/` no build final).
