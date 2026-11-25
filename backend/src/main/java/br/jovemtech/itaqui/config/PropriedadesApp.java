package br.jovemtech.itaqui.config;

import java.nio.file.Path;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record PropriedadesApp(Dados dados, Cors cors, Auth auth) {

    public record Dados(Path diretorio, String databaseUrl) {
        public Dados {
            if (diretorio == null) {
                diretorio = Path.of("./dados-runtime");
            }
            if (databaseUrl != null && databaseUrl.isBlank()) {
                databaseUrl = null;
            }
        }
    }

    public record Cors(List<String> origens) {
        public Cors {
            origens = origens == null ? List.of() : List.copyOf(origens);
        }
    }

    public record Auth(Boolean habilitada, String senhaAdmin, String senhaGestor) {
        public Auth {
            if (habilitada == null) {
                habilitada = Boolean.TRUE;
            }
            if (senhaAdmin == null || senhaAdmin.isBlank()) {
                senhaAdmin = "Itaqui@2026";
            }
            if (senhaGestor == null || senhaGestor.isBlank()) {
                senhaGestor = "Gestor@2026";
            }
        }
    }

    public PropriedadesApp {
        if (dados == null) {
            dados = new Dados(null, null);
        }
        if (cors == null) {
            cors = new Cors(null);
        }
        if (auth == null) {
            auth = new Auth(true, null, null);
        }
    }
}
