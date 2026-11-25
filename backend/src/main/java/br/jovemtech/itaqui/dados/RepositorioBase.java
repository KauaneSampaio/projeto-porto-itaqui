package br.jovemtech.itaqui.dados;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import br.jovemtech.itaqui.dominio.Base;

/**
 * Guarda a base atual. Na inicialização, usa a base gravada via {@link ArmazenamentoEstado}
 * (disco local ou PostgreSQL) se existir e for válida; senão, carrega a base fictícia do
 * classpath ({@code resources/dados}).
 */
@Repository
public class RepositorioBase {

    public static final String FONTE_SEMENTE = "SEMENTE";
    public static final String FONTE_GRAVADA = "GRAVADA";
    public static final String FONTE_IMPORTACAO = "IMPORTACAO";
    public static final String FONTE_EDITADA = "EDITADA";

    private static final Logger LOG = LoggerFactory.getLogger(RepositorioBase.class);

    private final ArmazenamentoEstado armazenamento;
    private final CarregadorBase carregador = new CarregadorBase();
    private final CsvEscritor escritor = new CsvEscritor();
    private final AtomicReference<Base> atual = new AtomicReference<>();

    public RepositorioBase(ArmazenamentoEstado armazenamento) {
        this.armazenamento = armazenamento;
        this.atual.set(carregarInicial());
    }

    public Base atual() {
        return atual.get();
    }

    /** Troca a base e grava no armazenamento persistente. */
    public synchronized void substituir(Base nova) {
        gravar(nova);
        atual.set(nova);
    }

    /** Volta para a base fictícia original do projeto. */
    public synchronized Base restaurarSemente() {
        Base semente = carregarSemente();
        substituir(semente);
        return semente;
    }

    public CarregadorBase.Resultado validar(Map<String, String> arquivos, String fonte) {
        return carregador.carregar(arquivos, fonte);
    }

    public Map<String, String> exportar() {
        return escritor.escrever(atual());
    }

    // ------------------------------------------------------------------

    private Base carregarInicial() {
        Optional<String> config = armazenamento.ler("base/" + ArquivosBase.CONFIG);
        if (config.isPresent() && !config.get().isBlank()) {
            try {
                Map<String, String> arquivos = new LinkedHashMap<>();
                for (String nome : ArquivosBase.TODOS) {
                    arquivos.put(nome, armazenamento.ler("base/" + nome).orElse(""));
                }
                CarregadorBase.Resultado r = carregador.carregar(arquivos, FONTE_GRAVADA);
                if (r.base() != null) {
                    LOG.info("Base carregada de {}", armazenamento.descricaoDestino("base"));
                    return r.base();
                }
                LOG.warn("Base gravada em {} é inválida ({} erro(s)); usando a base fictícia do projeto. "
                        + "Primeiro erro: {}", armazenamento.descricaoDestino("base"), r.relatorio().erros().size(),
                        r.relatorio().erros().get(0));
            } catch (RuntimeException e) {
                LOG.warn("Não foi possível ler a base gravada em {}: {}", armazenamento.descricaoDestino("base"), e.getMessage());
            }
        }
        Base semente = carregarSemente();
        LOG.info("Base fictícia do projeto carregada (corte {}, {} contas)", semente.dataCorte(),
                semente.contas().size());
        return semente;
    }

    private Base carregarSemente() {
        Map<String, String> arquivos = new LinkedHashMap<>();
        for (String nome : ArquivosBase.TODOS) {
            ClassPathResource res = new ClassPathResource("dados/" + nome);
            try (InputStream in = res.getInputStream()) {
                arquivos.put(nome, new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                arquivos.put(nome, "");
            }
        }
        CarregadorBase.Resultado r = carregador.carregar(arquivos, FONTE_SEMENTE);
        if (r.base() == null) {
            throw new IllegalStateException("A base fictícia em resources/dados é inválida: " + r.relatorio().erros());
        }
        return r.base();
    }

    private void gravar(Base base) {
        for (Map.Entry<String, String> e : escritor.escrever(base).entrySet()) {
            armazenamento.gravar("base/" + e.getKey(), e.getValue(), false);
        }
    }
}
