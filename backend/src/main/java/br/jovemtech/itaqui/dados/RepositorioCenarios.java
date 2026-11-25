package br.jovemtech.itaqui.dados;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.jovemtech.itaqui.dominio.Cenario;
import br.jovemtech.itaqui.dominio.Periodo;

/**
 * Cenários em memória, gravados via {@link ArmazenamentoEstado} (disco local ou PostgreSQL)
 * a cada alteração. Se ainda não houver cenários gravados, carrega a semente de demonstração
 * ({@code resources/dados/cenarios-semente.json}) quando disponível.
 */
@Repository
public class RepositorioCenarios {

    private static final String CHAVE = "cenarios.json";
    private static final Logger LOG = LoggerFactory.getLogger(RepositorioCenarios.class);
    private static final TypeReference<List<CenarioArquivo>> TIPO_LISTA = new TypeReference<>() {
    };

    /** Formato do arquivo: chaves simples para não depender de serialização de Periodo. */
    record CenarioArquivo(String id, String nome, Instant criadoEm, Instant atualizadoEm,
            Map<String, Map<String, BigDecimal>> ajustes) {
    }

    private final ArmazenamentoEstado armazenamento;
    private final ObjectMapper json;
    private final Cenario original = Cenario.original();
    private final Map<String, Cenario> cenarios = new ConcurrentHashMap<>();

    public RepositorioCenarios(ArmazenamentoEstado armazenamento, ObjectMapper json) {
        this.armazenamento = armazenamento;
        this.json = json;
        carregar();
    }

    public List<Cenario> listar() {
        List<Cenario> lista = new ArrayList<>();
        lista.add(original);
        cenarios.values().stream().sorted(Comparator.comparing(Cenario::criadoEm).thenComparing(Cenario::id))
                .forEach(lista::add);
        return lista;
    }

    public Optional<Cenario> buscar(String id) {
        if (Cenario.ID_ORIGINAL.equals(id)) {
            return Optional.of(original);
        }
        return Optional.ofNullable(cenarios.get(id));
    }

    public synchronized void salvar(Cenario cenario) {
        if (cenario.somenteLeitura()) {
            throw new IllegalArgumentException("Cenário somente leitura não pode ser gravado");
        }
        cenarios.put(cenario.id(), cenario);
        gravar();
    }

    public synchronized boolean remover(String id) {
        boolean removido = cenarios.remove(id) != null;
        if (removido) {
            gravar();
        }
        return removido;
    }

    // ------------------------------------------------------------------

    private void carregar() {
        Optional<String> salvo = armazenamento.ler(CHAVE);
        if (salvo.isPresent() && !salvo.get().isBlank()) {
            carregarDeJson(salvo.get(), armazenamento.descricaoDestino(CHAVE));
            return;
        }
        // Primeira inicialização sem cenários salvos: carrega a semente de demonstração
        ClassPathResource semente = new ClassPathResource("dados/cenarios-semente.json");
        if (semente.exists()) {
            try (InputStream in = semente.getInputStream()) {
                String conteudo = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                carregarDeJson(conteudo, "semente de demonstração");
                if (!cenarios.isEmpty()) {
                    gravar();
                }
            } catch (IOException e) {
                LOG.warn("Falha ao ler cenarios-semente.json: {}", e.getMessage());
            }
        }
    }

    private void carregarDeJson(String conteudo, String origem) {
        try {
            List<CenarioArquivo> lidos = json.readValue(conteudo, TIPO_LISTA);
            for (CenarioArquivo c : lidos) {
                Map<String, SortedMap<Periodo, BigDecimal>> ajustes = new LinkedHashMap<>();
                c.ajustes().forEach((premissa, valores) -> {
                    SortedMap<Periodo, BigDecimal> porPeriodo = new TreeMap<>();
                    valores.forEach((periodo, pct) -> porPeriodo.put(Periodo.parse(periodo), pct));
                    ajustes.put(premissa, porPeriodo);
                });
                cenarios.put(c.id(), new Cenario(c.id(), c.nome(), false, c.criadoEm(), c.atualizadoEm(), ajustes));
            }
            LOG.info("{} cenário(s) carregado(s) de {}", cenarios.size(), origem);
        } catch (IOException | RuntimeException e) {
            LOG.warn("Não foi possível ler {} ({}); começando sem cenários salvos", origem, e.getMessage());
        }
    }

    private void gravar() {
        List<CenarioArquivo> saida = new ArrayList<>();
        for (Cenario c : listar()) {
            if (c.somenteLeitura()) {
                continue;
            }
            Map<String, Map<String, BigDecimal>> ajustes = new LinkedHashMap<>();
            c.ajustes().forEach((premissa, valores) -> {
                Map<String, BigDecimal> porPeriodo = new LinkedHashMap<>();
                valores.forEach((periodo, pct) -> porPeriodo.put(periodo.toString(), pct));
                ajustes.put(premissa, porPeriodo);
            });
            saida.add(new CenarioArquivo(c.id(), c.nome(), c.criadoEm(), c.atualizadoEm(), ajustes));
        }
        try {
            String conteudo = json.writerWithDefaultPrettyPrinter().writeValueAsString(saida);
            armazenamento.gravar(CHAVE, conteudo, false);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao serializar cenários", e);
        }
    }
}
