# projeto-porto-itaqui
Projeto Porto do Itaqui feito no JV TECH.

## Plataforma Integrada de Projeção Orçamentária e EBITDA

PoC do desafio Jovem Tech 2026 — Porto do Itaqui / EMAP. **Todos os dados são fictícios.**

| Pasta / arquivo | Conteúdo |
|---|---|
| [docs/COMO_RODAR.md](docs/COMO_RODAR.md) | Passo a passo para rodar, testar e resolver problemas |
| [docs/GUIA_DO_CODIGO.md](docs/GUIA_DO_CODIGO.md) | O que cada parte do código faz e como foi feita |
| [ARQUITETURA.md](ARQUITETURA.md) | Arquitetura, decisões e pendências |
| [backend/](backend/README.md) | API e motor de cálculo (Java 21 + Spring Boot) |
| [frontend/](frontend/README.md) | Interface (HTML/CSS/JS + Chart.js), baseada no front do Kayo (`Kayoyxz/Porto-Itaqui-v2`) |

Há também o back-end do Alexandre na branch `Branch-Backend-Alexandre`, que ainda precisa ser reconciliado com este.

### Rodar

Precisa do JDK 21 (`sudo apt install openjdk-21-jdk-headless`).

```bash
cd backend && ./mvnw spring-boot:run
```

Abra `http://localhost:8080`. O back-end serve também o front-end.

```bash
cd backend && ./mvnw test
```
