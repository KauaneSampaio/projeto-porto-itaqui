# MeuEbitify — Plataforma de Projeção Orçamentária e EBITDA

Plataforma web de projeção orçamentária, DRE e análise de EBITDA desenvolvida para o desafio **Jovem Tech 2026 — Porto do Itaqui / EMAP**.  
**Todos os dados da base padrão são fictícios para demonstração.**

## Funcionalidades principais

- **Visão Geral**: cards de EBITDA Forecast × Orçado, desvio, margem EBITDA e gráfico triplo mensal (Orçado × Realizado × Forecast).
- **DRE Completa**: 53 contas hierárquicas da EMAP (2024–2027), meses fechados preservados até a data de corte (`set/2026`), destaque de OPEX e índices vinculados.
- **Comparativo**: painel triplo por indicador/conta, desvios por grupo e comparativo conta a conta entre cenário simulado e orçamento oficial.
- **Premissas e Cenários**: fluxo guiado em 3 passos, atalhos de edição (`+0,5 p.p.`, `−0,5 p.p.`, preenchimento em lote), barra de alterações pendentes, cards de impacto no EBITDA e opção auditada de liberação de edição do orçamento oficial.
- **Fechamento Mensal Simplificado**: lançamento direto do realizado do mês pela interface em *Configurações → Fechamento mensal* (com sugestão do orçado).
- **Segurança e Auditoria**: login com hash PBKDF2-HMAC-SHA256 (210.000 iterações), sessão `HttpOnly`, proteção CSRF (`X-CSRF-Token`), rate limit contra força bruta, gestão de usuários exclusiva para `ADMIN`, troca de senha e **Logs de auditoria** com o usuário autenticado.
- **Evidências (Admin)**: execução automática dos casos de teste CT-01 a CT-07, medição de desempenho (< 3 s) e exportação CSV no layout da Planilha Oráculo.

## Documentação

| Pasta / arquivo | Conteúdo |
|---|---|
| [docs/GUIA_DE_USO.md](docs/GUIA_DE_USO.md) | Guia prático de uso de cada tela e roteiro de demonstração |
| [docs/COMO_RODAR.md](docs/COMO_RODAR.md) | Passo a passo para rodar, testar e resolver problemas |
| [docs/GUIA_DO_CODIGO.md](docs/GUIA_DO_CODIGO.md) | O que cada parte do código faz e como foi feita |
| [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) | Contexto técnico completo e decisões arquiteturais |
| [ARQUITETURA.md](ARQUITETURA.md) | Arquitetura, contratos e regras de cálculo |
| [backend/](backend/README.md) | API REST, segurança e motor de cálculo (Java 21 + Spring Boot 3.4.5) |
| [frontend/](frontend/README.md) | Interface web (HTML/CSS/JS puro + Chart.js) |

## Como rodar

Requer **JDK 21** (`sudo apt install openjdk-21-jdk-headless`).

```bash
cd backend && ./mvnw spring-boot:run
```

Acesse `http://localhost:8080` (você será direcionado para a tela de login). O back-end serve também o front-end.

## Testes automatizados

```bash
cd backend && ./mvnw test
```

Executa os **49 testes** (`ApiTest`, `SegurancaTest`, `OraculoTest`, `CasosDeTesteMotorTest`, `CarregadorBaseTest`).

## Variáveis de ambiente para produção (HTTPS)

- `APP_COOKIE_SECURE=true` — restringe o cookie de sessão a conexões HTTPS.
- `APP_AUTH_SENHA_ADMIN` e `APP_AUTH_SENHA_GESTOR` — senhas iniciais na primeira subida (antes de gerar `dados-runtime/usuarios.json`).
- `APP_CORS_ORIGENS=` — deixe vazio em produção quando front-end e back-end rodarem no mesmo domínio.
