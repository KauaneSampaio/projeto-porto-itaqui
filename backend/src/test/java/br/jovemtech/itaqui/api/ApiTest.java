package br.jovemtech.itaqui.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.jovemtech.itaqui.dados.ArquivosSemente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercita todos os endpoints da API, incluindo os caminhos de erro. */
@SpringBootTest(properties = "app.auth.habilitada=false")
@AutoConfigureMockMvc
class ApiTest {

    private static final Path DIRETORIO;

    static {
        try {
            DIRETORIO = Files.createTempDirectory("itaqui-api-teste");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        registry.add("app.dados.diretorio", DIRETORIO::toString);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @BeforeEach
    void restaurarBase() throws Exception {
        mvc.perform(post("/api/base/restaurar")).andExpect(status().isOk());
    }

    private String criarCenario(String nome) throws Exception {
        String corpo = mvc.perform(post("/api/cenarios").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"" + nome + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("id").asText();
    }

    private JsonNode getJson(String url) throws Exception {
        String corpo = mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(corpo);
    }

    // ------------------------------------------------------------------ meta e estrutura

    @Test
    void meta() throws Exception {
        mvc.perform(get("/api/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataCorte").value("2026-09"))
                .andExpect(jsonPath("$.dadosFicticios").value(true))
                .andExpect(jsonPath("$.origemDados").value("FICTICIO"))
                .andExpect(jsonPath("$.aviso").value(containsString("FICTÍCIOS")))
                .andExpect(jsonPath("$.anos[0]").value(2024));
    }

    @Test
    void estrutura() throws Exception {
        mvc.perform(get("/api/dre/estrutura"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("1"))
                .andExpect(jsonPath("$[0].filhos[0].codigo").value("1.1"));
    }

    @Test
    void cadastrarERemoverConta() throws Exception {
        String conta = "{\"codigo\":\"4.99\",\"nome\":\"Treinamento\",\"codigoPai\":\"4\","
                + "\"tipo\":\"DESPESA_OPERACIONAL\",\"opex\":true}";
        mvc.perform(post("/api/dre/contas").contentType(MediaType.APPLICATION_JSON).content(conta))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("4.99"));
        mvc.perform(post("/api/dre/contas").contentType(MediaType.APPLICATION_JSON).content(conta))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("CONTA_DUPLICADA"));
        mvc.perform(delete("/api/dre/contas/4.99")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/dre/contas/4.99")).andExpect(status().isNotFound());
    }

    @Test
    void contaInvalida() throws Exception {
        mvc.perform(post("/api/dre/contas").contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"3.99\",\"nome\":\"X\",\"codigoPai\":\"3\",\"tipo\":\"RECEITA_BRUTA\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("ESTRUTURA_INVALIDA"));
        mvc.perform(post("/api/dre/contas").contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"\",\"nome\":\"X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("REQUISICAO_INVALIDA"));
        mvc.perform(delete("/api/dre/contas/1.1.1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("CONTA_COM_VALORES"));
    }

    // ------------------------------------------------------------------ premissas e vínculos

    @Test
    void premissasEVinculos() throws Exception {
        mvc.perform(get("/api/premissas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigo", hasItem("IPCA")));
        mvc.perform(get("/api/premissas/IPCA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contasVinculadas", hasItem("3.10")));
        mvc.perform(get("/api/premissas/NAO")).andExpect(status().isNotFound());

        mvc.perform(post("/api/premissas").contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"SELIC\",\"nome\":\"Selic\",\"valores\":["
                        + "{\"periodo\":\"2026-10\",\"percentual\":10.5},{\"periodo\":\"2026-11\",\"percentual\":10.5}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valores.length()").value(2));
        mvc.perform(post("/api/premissas").contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"SELIC\",\"nome\":\"Selic\",\"valores\":[{\"periodo\":\"2026-10\",\"percentual\":1}]}"))
                .andExpect(status().isConflict());

        String vinculo = "{\"codigoConta\":\"4.8\",\"codigoPremissa\":\"SELIC\",\"vigenciaInicio\":\"2026-10\"}";
        mvc.perform(post("/api/vinculos").contentType(MediaType.APPLICATION_JSON).content(vinculo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nomeConta").value("D. Transporte e Locomoção"));
        mvc.perform(post("/api/vinculos").contentType(MediaType.APPLICATION_JSON).content(vinculo))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/vinculos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigoPremissa", hasItem("SELIC")));
        mvc.perform(post("/api/vinculos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigoConta\":\"4\",\"codigoPremissa\":\"IPCA\",\"vigenciaInicio\":\"2026-10\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("CONTA_GRUPO"));
        mvc.perform(delete("/api/vinculos").param("conta", "4.8").param("premissa", "SELIC"))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/vinculos").param("conta", "4.8").param("premissa", "SELIC"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ cenários e cálculo

    @Test
    @DisplayName("Fluxo completo de cenário: criar, ajustar, calcular, comparar, renomear, remover")
    void fluxoCenario() throws Exception {
        JsonNode antes = getJson("/api/cenarios/original/dre?ano=2026");
        String id = criarCenario("IPCA alto");

        mvc.perform(get("/api/cenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("original"))
                .andExpect(jsonPath("$[*].id", hasItem(id)));

        mvc.perform(put("/api/cenarios/" + id + "/premissas/IPCA").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ajustes\":[{\"periodo\":\"2026-10\",\"percentual\":9.0},"
                        + "{\"periodo\":\"2026-11\",\"percentual\":9.0},{\"periodo\":\"2026-12\",\"percentual\":9.0}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesesAjustados").value(3));

        JsonNode dre = getJson("/api/cenarios/" + id + "/dre?ano=2026");
        assertThat(dre.get("origemDados").asText()).isEqualTo("FICTICIO");
        assertThat(dre.get("meses").get(8).get("status").asText()).isEqualTo("FECHADO");
        assertThat(dre.get("meses").get(9).get("status").asText()).isEqualTo("ABERTO");
        assertThat(dre.get("indicadores").get("ebitda").get("forecast").decimalValue())
                .isLessThan(antes.get("indicadores").get("ebitda").get("forecast").decimalValue());
        assertThat(dre.get("indicadores").get("ebitda").get("orcado").decimalValue())
                .isEqualByComparingTo(antes.get("indicadores").get("ebitda").get("orcado").decimalValue());

        // CT-05: o original continua idêntico
        JsonNode depois = getJson("/api/cenarios/original/dre?ano=2026");
        assertThat(depois.get("linhas")).isEqualTo(antes.get("linhas"));
        assertThat(depois.get("indicadores")).isEqualTo(antes.get("indicadores"));

        mvc.perform(get("/api/cenarios/" + id + "/comparativo").param("ano", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseId").value("original"))
                .andExpect(jsonPath("$.linhasAlteradas", greaterThan(0)))
                .andExpect(jsonPath("$.indicadores[3].indicador").value("ebitda"));

        mvc.perform(get("/api/cenarios/" + id + "/premissas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo=='IPCA')].valores[?(@.alterado==true)].periodo",
                        hasItem("2026-10")));

        mvc.perform(patch("/api/cenarios/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"IPCA 9%\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("IPCA 9%"));

        mvc.perform(delete("/api/cenarios/" + id + "/premissas/IPCA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesesAjustados").value(0));

        mvc.perform(delete("/api/cenarios/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/cenarios/" + id)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("O cenário original é somente leitura (RN006)")
    void originalSomenteLeitura() throws Exception {
        mvc.perform(put("/api/cenarios/original/premissas/IPCA").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ajustes\":[{\"periodo\":\"2026-10\",\"percentual\":9.0}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("CENARIO_SOMENTE_LEITURA"));
        mvc.perform(delete("/api/cenarios/original")).andExpect(status().isConflict());
        mvc.perform(patch("/api/cenarios/original").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"x\"}")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Ajuste em mês fechado é recusado (RN003)")
    void ajusteEmMesFechado() throws Exception {
        String id = criarCenario("Teste mês fechado");
        mvc.perform(put("/api/cenarios/" + id + "/premissas/IPCA").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ajustes\":[{\"periodo\":\"2026-09\",\"percentual\":9.0}]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("PERIODO_FECHADO"));
        mvc.perform(put("/api/cenarios/" + id + "/premissas/IPCA").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ajustes\":[{\"periodo\":\"2026-99\",\"percentual\":9.0}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/cenarios/" + id + "/premissas/NAO").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ajustes\":[{\"periodo\":\"2026-10\",\"percentual\":9.0}]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cenarioCopiadoENomeRepetido() throws Exception {
        String origem = criarCenario("Origem da cópia");
        mvc.perform(put("/api/cenarios/" + origem + "/premissas/IPCA").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ajustes\":[{\"periodo\":\"2027-01\",\"percentual\":2.0}]}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/cenarios").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Cópia\",\"copiarDe\":\"" + origem + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ajustes.IPCA[0].periodo").value("2027-01"));
        mvc.perform(post("/api/cenarios").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"origem da CÓPIA\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("NOME_EM_USO"));
    }

    @Test
    void anoSemDados() throws Exception {
        mvc.perform(get("/api/cenarios/original/dre").param("ano", "2030"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/cenarios/original/dre"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/cenarios/original/dre").param("ano", "abc"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ importação

    private MockMultipartHttpServletRequestBuilder upload(Map<String, String> arquivos) {
        MockMultipartHttpServletRequestBuilder req = multipart("/api/importacao");
        arquivos.forEach((nome, conteudo) -> req.file(new MockMultipartFile(nome.replace(".csv", ""), nome,
                "text/csv", conteudo.getBytes(StandardCharsets.UTF_8))));
        return req;
    }

    @Test
    @DisplayName("CT-07 Importação inválida devolve 422 e mantém a base atual")
    void importacaoInvalida() throws Exception {
        Map<String, String> arquivos = ArquivosSemente.ler();
        arquivos.put("valores.csv", arquivos.get("valores.csv") + "3.1;2027;5;REALIZADO;1,00\n");
        mvc.perform(upload(arquivos))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.valido").value(false))
                .andExpect(jsonPath("$.aplicado").value(false))
                .andExpect(jsonPath("$.erros[0].arquivo").value("valores.csv"));
        mvc.perform(get("/api/meta")).andExpect(jsonPath("$.fonteBase").value("SEMENTE"));
    }

    @Test
    void importacaoSimuladaEAplicada() throws Exception {
        Map<String, String> arquivos = ArquivosSemente.ler();
        arquivos.put("config.csv", "chave;valor\ndata_corte;2026-06\norigem;FICTICIO\nunidade;R$ mil\nversao;2\n");
        arquivos.put("valores.csv", arquivos.get("valores.csv").lines()
                .filter(l -> !(l.contains(";2026;7;REALIZADO") || l.contains(";2026;8;REALIZADO")
                        || l.contains(";2026;9;REALIZADO")))
                .reduce("", (a, b) -> a + b + "\n"));

        mvc.perform(upload(arquivos).param("simular", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(true))
                .andExpect(jsonPath("$.aplicado").value(false));
        mvc.perform(get("/api/meta")).andExpect(jsonPath("$.dataCorte").value("2026-09"));

        mvc.perform(upload(arquivos))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aplicado").value(true))
                .andExpect(jsonPath("$.resumo.dataCorte").value("2026-06"));
        mvc.perform(get("/api/meta"))
                .andExpect(jsonPath("$.dataCorte").value("2026-06"))
                .andExpect(jsonPath("$.versaoBase").value(2))
                .andExpect(jsonPath("$.fonteBase").value("IMPORTACAO"));
    }

    @Test
    void importacaoSemArquivos() throws Exception {
        mvc.perform(multipart("/api/importacao"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erros.length()").value(5));
    }

    // ------------------------------------------------------------------ exportação e evidências

    @Test
    void exportarArquivosDaBase() throws Exception {
        mvc.perform(get("/api/base/arquivos/contas.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("contas.csv")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(containsString("codigo;nome;codigo_pai;tipo;opex;ordem")));
        mvc.perform(get("/api/base/arquivos/senhas.csv")).andExpect(status().isNotFound());
    }

    @Test
    void evidencias() throws Exception {
        String indicadores = mvc.perform(get("/api/evidencias/indicadores.csv").param("ano", "2026"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(indicadores).contains("cenario;ano;mes;indicador;perspectiva;valor;origem_dados")
                .contains("original;2026;TOTAL;ebitda;forecast;").contains(";FICTICIO");

        String contas = mvc.perform(get("/api/evidencias/contas.csv").param("ano", "2026"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(contas).contains("original;2026;10;1.1.1;Movimentação Carga - TEGRAM;2;ABERTO;");

        mvc.perform(get("/api/evidencias/desempenho").param("ano", "2026").param("repeticoes", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repeticoes").value(5))
                .andExpect(jsonPath("$.dentroDaMeta").value(true));
    }

    // ------------------------------------------------------------------ erros genéricos

    @Test
    void errosGenericosEmJson() throws Exception {
        mvc.perform(get("/api/nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("NAO_ENCONTRADO"));
        mvc.perform(post("/api/cenarios").contentType(MediaType.APPLICATION_JSON).content("{nome"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("REQUISICAO_INVALIDA"));
        mvc.perform(delete("/api/meta"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("Cenários e base editada sobrevivem em disco")
    void persistencia() throws Exception {
        String id = criarCenario("Persistido");
        assertThat(Files.readString(DIRETORIO.resolve("cenarios.json"))).contains(id);
        assertThat(Files.exists(DIRETORIO.resolve("base").resolve("contas.csv"))).isTrue();
    }
}
