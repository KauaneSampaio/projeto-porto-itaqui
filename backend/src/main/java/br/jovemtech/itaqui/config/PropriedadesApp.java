package br.jovemtech.itaqui.config;

import java.nio.file.Path;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record PropriedadesApp(Dados dados, Cors cors) {

    public record Dados(Path diretorio) {
        public Dados {
            if (diretorio == null) {
                diretorio = Path.of("./dados-runtime");
            }
        }
    }

    public record Cors(List<String> origens) {
        public Cors {
            origens = origens == null ? List.of() : List.copyOf(origens);
        }
    }

    public PropriedadesApp {
        if (dados == null) {
            dados = new Dados(null);
        }
        if (cors == null) {
            cors = new Cors(null);
        }
    }
}
