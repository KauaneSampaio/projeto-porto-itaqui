package br.jovemtech.itaqui.dados;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Lê os CSVs da base fictícia do projeto (src/main/resources/dados). */
public final class ArquivosSemente {

    private ArquivosSemente() {
    }

    public static Map<String, String> ler() {
        Map<String, String> arquivos = new LinkedHashMap<>();
        for (String nome : ArquivosBase.TODOS) {
            try (InputStream in = ArquivosSemente.class.getClassLoader().getResourceAsStream("dados/" + nome)) {
                arquivos.put(nome, new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return arquivos;
    }
}
