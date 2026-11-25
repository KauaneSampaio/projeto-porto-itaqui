package br.jovemtech.itaqui.dados;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import br.jovemtech.itaqui.config.PropriedadesApp;

/**
 * Camada única de persistência para usuários, logs, cenários e base editada.
 * <ul>
 *   <li>Se {@code DATABASE_URL} ({@code app.dados.database-url}) estiver configurada, persiste
 *       em PostgreSQL (ex.: Neon Serverless Free) — sobrevive a hibernações e redeploys no Render.</li>
 *   <li>Caso contrário, grava atomicamente em arquivos locais em {@code app.dados.diretorio}.</li>
 * </ul>
 */
@Component
public class ArmazenamentoEstado {

    private static final Logger LOG = LoggerFactory.getLogger(ArmazenamentoEstado.class);

    private static final String SQL_CRIAR_TABELA = """
            CREATE TABLE IF NOT EXISTS ebitify_estado (
                chave VARCHAR(120) PRIMARY KEY,
                conteudo TEXT NOT NULL,
                atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
            """;

    private static final String SQL_LER = "SELECT conteudo FROM ebitify_estado WHERE chave = ?";

    private static final String SQL_GRAVAR = """
            INSERT INTO ebitify_estado (chave, conteudo, atualizado_em)
            VALUES (?, ?, NOW())
            ON CONFLICT (chave) DO UPDATE
            SET conteudo = EXCLUDED.conteudo, atualizado_em = NOW()
            """;

    private final Path diretorioLocal;
    private final String jdbcUrl;
    private final Properties jdbcProps;
    private final boolean usaBanco;

    public ArmazenamentoEstado(PropriedadesApp props) {
        this.diretorioLocal = props.dados().diretorio();
        String dbUrl = props.dados().databaseUrl();
        if (dbUrl != null && !dbUrl.isBlank()) {
            ConexaoConfig cfg = converterUrl(dbUrl.trim());
            this.jdbcUrl = cfg.jdbcUrl();
            this.jdbcProps = cfg.props();
            this.usaBanco = true;
            inicializarTabela();
            LOG.info("Persistência em PostgreSQL ativada (sobrevive a hibernação e redeploy)");
        } else {
            this.jdbcUrl = null;
            this.jdbcProps = null;
            this.usaBanco = false;
            LOG.info("Persistência em disco local ativada em {}", diretorioLocal.toAbsolutePath());
        }
    }

    public boolean usaBanco() {
        return usaBanco;
    }

    public String descricaoDestino(String chave) {
        return usaBanco ? "PostgreSQL[" + chave + "]" : diretorioLocal.resolve(chave).toAbsolutePath().toString();
    }

    public synchronized Optional<String> ler(String chave) {
        if (usaBanco) {
            try (Connection c = abrirConexao();
                 PreparedStatement ps = c.prepareStatement(SQL_LER)) {
                ps.setString(1, chave);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.ofNullable(rs.getString(1));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Falha ao ler '" + chave + "' no PostgreSQL: " + e.getMessage(), e);
            }
        }
        Path p = diretorioLocal.resolve(chave);
        if (!Files.isRegularFile(p)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(p, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler " + p.toAbsolutePath(), e);
        }
    }

    public synchronized void gravar(String chave, String conteudo, boolean restrito) {
        if (usaBanco) {
            try (Connection c = abrirConexao();
                 PreparedStatement ps = c.prepareStatement(SQL_GRAVAR)) {
                ps.setString(1, chave);
                ps.setString(2, conteudo);
                ps.executeUpdate();
                return;
            } catch (SQLException e) {
                throw new IllegalStateException("Falha ao gravar '" + chave + "' no PostgreSQL: " + e.getMessage(), e);
            }
        }
        Path destino = diretorioLocal.resolve(chave);
        try {
            Files.createDirectories(destino.getParent());
            Path tmp = destino.resolveSibling(destino.getFileName() + ".tmp");
            Files.writeString(tmp, conteudo, StandardCharsets.UTF_8);
            Files.move(tmp, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            if (restrito) {
                restringirPermissao(destino);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar " + destino.toAbsolutePath(), e);
        }
    }

    private void inicializarTabela() {
        try (Connection c = abrirConexao(); Statement st = c.createStatement()) {
            st.execute(SQL_CRIAR_TABELA);
        } catch (SQLException e) {
            throw new IllegalStateException("Não foi possível inicializar a tabela ebitify_estado no PostgreSQL: " + e.getMessage(), e);
        }
    }

    private Connection abrirConexao() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, jdbcProps);
    }

    private static void restringirPermissao(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
        }
    }

    record ConexaoConfig(String jdbcUrl, Properties props) {
    }

    /**
     * Aceita URLs no formato do Neon/Render ({@code postgres://user:pass@host/db?sslmode=require}
     * ou {@code postgresql://...}) ou já no formato JDBC ({@code jdbc:postgresql://...}).
     */
    static ConexaoConfig converterUrl(String url) {
        Properties p = new Properties();
        p.setProperty("connectTimeout", "15");
        p.setProperty("socketTimeout", "30");

        if (url.startsWith("jdbc:")) {
            return new ConexaoConfig(url, p);
        }
        String normalizada = url.startsWith("postgres://")
                ? "postgresql://" + url.substring("postgres://".length())
                : url;
        URI uri = URI.create(normalizada);
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null && !userInfo.isEmpty()) {
            int sep = userInfo.indexOf(':');
            if (sep >= 0) {
                p.setProperty("user", decodificar(userInfo.substring(0, sep)));
                p.setProperty("password", decodificar(userInfo.substring(sep + 1)));
            } else {
                p.setProperty("user", decodificar(userInfo));
            }
        }
        String host = uri.getHost();
        int porta = uri.getPort() > 0 ? uri.getPort() : 5432;
        String caminho = uri.getPath() != null ? uri.getPath() : "";
        String query = uri.getRawQuery();
        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(host).append(':').append(porta).append(caminho);
        if (query != null && !query.isBlank()) {
            jdbc.append('?').append(query);
        } else {
            jdbc.append("?sslmode=require");
        }
        return new ConexaoConfig(jdbc.toString(), p);
    }

    private static String decodificar(String valor) {
        return URLDecoder.decode(valor, StandardCharsets.UTF_8);
    }
}
