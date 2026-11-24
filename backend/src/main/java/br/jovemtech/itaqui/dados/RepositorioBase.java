package br.jovemtech.itaqui.dados;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import br.jovemtech.itaqui.config.PropriedadesApp;
import br.jovemtech.itaqui.dominio.Base;

/**
 * Guarda a base atual. Na inicialização, usa a base gravada em
 * {@code <app.dados.diretorio>/base/} se existir e for válida; senão, a base fictícia do
 * classpath ({@code resources/dados}). Toda substituição é gravada em disco.
 */
@Repository
public class RepositorioBase {

    public static final String FONTE_SEMENTE = "SEMENTE";
    public static final String FONTE_GRAVADA = "GRAVADA";
    public static final String FONTE_IMPORTACAO = "IMPORTACAO";
    public static final String FONTE_EDITADA = "EDITADA";

    private static final Logger LOG = LoggerFactory.getLogger(RepositorioBase.class);

    private final Path diretorio;
    private final CarregadorBase carregador = new CarregadorBase();
    private final CsvEscritor escritor = new CsvEscritor();
    private final AtomicReference<Base> atual = new AtomicReference<>();

    public RepositorioBase(PropriedadesApp props) {
        this.diretorio = props.dados().diretorio().resolve("base");
        this.atual.set(carregarInicial());
    }

    public Base atual() {
        return atual.get();
    }

    /** Troca a base e grava em disco. */
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
        if (Files.exists(diretorio.resolve(ArquivosBase.CONFIG))) {
            try {
                Map<String, String> arquivos = new LinkedHashMap<>();
                for (String nome : ArquivosBase.TODOS) {
                    Path p = diretorio.resolve(nome);
                    arquivos.put(nome, Files.exists(p) ? Files.readString(p, StandardCharsets.UTF_8) : "");
                }
                CarregadorBase.Resultado r = carregador.carregar(arquivos, FONTE_GRAVADA);
                if (r.base() != null) {
                    LOG.info("Base carregada de {}", diretorio.toAbsolutePath());
                    return r.base();
                }
                LOG.warn("Base gravada em {} é inválida ({} erro(s)); usando a base fictícia do projeto. "
                        + "Primeiro erro: {}", diretorio.toAbsolutePath(), r.relatorio().erros().size(),
                        r.relatorio().erros().get(0));
            } catch (IOException e) {
                LOG.warn("Não foi possível ler a base gravada em {}: {}", diretorio.toAbsolutePath(), e.getMessage());
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
        try {
            Files.createDirectories(diretorio);
            for (Map.Entry<String, String> e : escritor.escrever(base).entrySet()) {
                Path destino = diretorio.resolve(e.getKey());
                Path temp = diretorio.resolve(e.getKey() + ".tmp");
                Files.writeString(temp, e.getValue(), StandardCharsets.UTF_8);
                Files.move(temp, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar a base em " + diretorio.toAbsolutePath(), e);
        }
    }
}
