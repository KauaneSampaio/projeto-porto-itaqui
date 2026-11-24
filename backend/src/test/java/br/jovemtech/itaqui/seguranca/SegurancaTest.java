package br.jovemtech.itaqui.seguranca;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SegurancaTest {

    @TempDir
    static Path diretorio;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("app.dados.diretorio", () -> diretorio.toString());
        r.add("app.auth.habilitada", () -> "true");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    LimitadorTentativas limitador;

    @BeforeEach
    void limparLimitador() {
        limitador.limparTudo();
    }

    @Test
    @DisplayName("Sem login, API retorna 401 e páginas internas redirecionam para /login.html")
    void bloqueiaSemLogin() throws Exception {
        mvc.perform(get("/api/meta"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("NAO_AUTENTICADO"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().string("Cache-Control", containsString("no-store")));

        mvc.perform(get("/api/cenarios")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/logs")).andExpect(status().isUnauthorized());

        mvc.perform(get("/"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login.html"));

        mvc.perform(get("/index.html"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login.html"));

        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autenticado").value(false));
    }

    @Test
    @DisplayName("Bloqueia após 5 tentativas de login inválidas (força bruta)")
    void rateLimitForcaBruta() throws Exception {
        String corpoInvalido = "{\"usuario\":\"admin\",\"senha\":\"errada123\"}";
        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(corpoInvalido))
                    .andExpect(status().isUnauthorized());
        }
        // 5ª falha dispara bloqueio 429
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(corpoInvalido))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.erro").value("MUITAS_TENTATIVAS"));

        // Mesmo com a senha correta, continua bloqueado durante a janela
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"admin\",\"senha\":\"Itaqui@2026\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Fluxo completo: login, CSRF obrigatório em mutações, auditoria com usuário real e logout")
    void fluxoAutenticadoECsrf() throws Exception {
        MvcResult loginRes = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"admin\",\"senha\":\"Itaqui@2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autenticado").value(true))
                .andExpect(jsonPath("$.usuario").value("admin"))
                .andReturn();

        MockHttpSession sessao = (MockHttpSession) loginRes.getRequest().getSession(false);
        assertThat(sessao).isNotNull();
        JsonNode body = json.readTree(loginRes.getResponse().getContentAsString());
        String csrf = body.get("csrfToken").asText();
        assertThat(csrf).isNotBlank();

        // Leitura autenticada liberada
        mvc.perform(get("/api/meta").session(sessao))
                .andExpect(status().isOk());

        // Mutação SEM token CSRF -> 403 Forbidden
        mvc.perform(post("/api/cenarios").session(sessao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Sem CSRF\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro").value("CSRF_INVALIDO"));

        // Mutação COM token CSRF -> 201 Created
        mvc.perform(post("/api/cenarios").session(sessao)
                        .header(FiltroAutenticacao.HEADER_CSRF, csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Cenario Seguro\"}"))
                .andExpect(status().isCreated());

        // Logs de auditoria registram o usuário autenticado real ("admin")
        mvc.perform(get("/api/logs").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.operacao=='CRIAR_CENARIO')].usuario", hasItem("admin")))
                .andExpect(jsonPath("$[?(@.operacao=='LOGIN')].usuario", hasItem("admin")));

        // Logout invalida sessão
        mvc.perform(post("/api/auth/logout").session(sessao))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/meta").session(sessao))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Gestão de usuários: GESTOR recebe 403; ADMIN cria, testa login do novo usuário e remove")
    void gestaoUsuariosSomenteAdmin() throws Exception {
        // Login como GESTOR
        MvcResult gestorRes = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"gestor.emap\",\"senha\":\"Gestor@2026\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession sessaoGestor = (MockHttpSession) gestorRes.getRequest().getSession(false);
        String csrfGestor = json.readTree(gestorRes.getResponse().getContentAsString()).get("csrfToken").asText();

        mvc.perform(get("/api/auth/usuarios").session(sessaoGestor))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro").value("ACESSO_NEGADO"));
        mvc.perform(get("/api/evidencias/desempenho?ano=2026").session(sessaoGestor))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro").value("ACESSO_NEGADO"));
        mvc.perform(post("/api/auth/usuarios").session(sessaoGestor)
                        .header(FiltroAutenticacao.HEADER_CSRF, csrfGestor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"intruso\",\"nome\":\"Intruso\",\"perfil\":\"ADMIN\",\"senha\":\"Senha1234\"}"))
                .andExpect(status().isForbidden());

        // Login como ADMIN
        MvcResult adminRes = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"admin\",\"senha\":\"Itaqui@2026\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession sessaoAdmin = (MockHttpSession) adminRes.getRequest().getSession(false);
        String csrfAdmin = json.readTree(adminRes.getResponse().getContentAsString()).get("csrfToken").asText();

        // ADMIN cria nova conta
        mvc.perform(post("/api/auth/usuarios").session(sessaoAdmin)
                        .header(FiltroAutenticacao.HEADER_CSRF, csrfAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"analista.gfin\",\"nome\":\"Maria Silva\",\"perfil\":\"GESTOR\",\"senha\":\"Analista@2026\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.login").value("analista.gfin"))
                .andExpect(jsonPath("$.hashSenha").doesNotExist());

        // Novo usuário consegue fazer login
        MvcResult novoRes = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"analista.gfin\",\"senha\":\"Analista@2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value("analista.gfin"))
                .andReturn();
        MockHttpSession sessaoNovo = (MockHttpSession) novoRes.getRequest().getSession(false);

        // ADMIN não pode excluir a si mesmo
        mvc.perform(delete("/api/auth/usuarios/admin").session(sessaoAdmin)
                        .header(FiltroAutenticacao.HEADER_CSRF, csrfAdmin))
                .andExpect(status().isConflict());

        // ADMIN exclui a conta criada -> sessão já ativa do usuário excluído é revogada imediatamente
        mvc.perform(delete("/api/auth/usuarios/analista.gfin").session(sessaoAdmin)
                        .header(FiltroAutenticacao.HEADER_CSRF, csrfAdmin))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/meta").session(sessaoNovo))
                .andExpect(status().isUnauthorized());
    }
}
