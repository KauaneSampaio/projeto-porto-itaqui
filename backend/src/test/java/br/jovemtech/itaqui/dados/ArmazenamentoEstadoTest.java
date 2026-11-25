package br.jovemtech.itaqui.dados;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import br.jovemtech.itaqui.config.PropriedadesApp;

import static org.assertj.core.api.Assertions.assertThat;

class ArmazenamentoEstadoTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Converte corretamente URLs do Neon/Render (postgres:// e postgresql://) para JDBC com SSL")
    void converteUrlNeonERender() {
        var cfg1 = ArmazenamentoEstado.converterUrl(
                "postgresql://neondb_owner:s%40nha123@ep-cool-host.us-east-2.aws.neon.tech/neondb?sslmode=require");
        assertThat(cfg1.jdbcUrl())
                .isEqualTo("jdbc:postgresql://ep-cool-host.us-east-2.aws.neon.tech:5432/neondb?sslmode=require");
        assertThat(cfg1.props().getProperty("user")).isEqualTo("neondb_owner");
        assertThat(cfg1.props().getProperty("password")).isEqualTo("s@nha123");

        var cfg2 = ArmazenamentoEstado.converterUrl("postgres://user:pass@db.exemplo.com:6432/ebitify");
        assertThat(cfg2.jdbcUrl()).isEqualTo("jdbc:postgresql://db.exemplo.com:6432/ebitify?sslmode=require");
        assertThat(cfg2.props().getProperty("user")).isEqualTo("user");
        assertThat(cfg2.props().getProperty("password")).isEqualTo("pass");

        var cfg3 = ArmazenamentoEstado.converterUrl("jdbc:postgresql://localhost:5432/ebitify");
        assertThat(cfg3.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/ebitify");
    }

    @Test
    @DisplayName("Sem DATABASE_URL, grava e lê atomicamente em disco local")
    void persistenciaDiscoLocal() {
        PropriedadesApp props = new PropriedadesApp(new PropriedadesApp.Dados(tempDir, null), null, null);
        ArmazenamentoEstado arm = new ArmazenamentoEstado(props);
        assertThat(arm.usaBanco()).isFalse();
        assertThat(arm.ler("usuarios.json")).isEmpty();

        arm.gravar("usuarios.json", "[{\"login\":\"admin\"}]", true);
        assertThat(arm.ler("usuarios.json")).contains("[{\"login\":\"admin\"}]");
    }
}
