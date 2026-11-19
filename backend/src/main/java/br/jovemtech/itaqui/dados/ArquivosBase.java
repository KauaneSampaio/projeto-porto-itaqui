package br.jovemtech.itaqui.dados;

import java.util.List;

/** Nomes dos arquivos CSV que formam uma base (contrato com a equipe de Dados). */
public final class ArquivosBase {

    public static final String CONFIG = "config.csv";
    public static final String CONTAS = "contas.csv";
    public static final String VALORES = "valores.csv";
    public static final String PREMISSAS = "premissas.csv";
    public static final String VINCULOS = "vinculos.csv";

    public static final List<String> TODOS = List.of(CONFIG, CONTAS, VALORES, PREMISSAS, VINCULOS);

    private ArquivosBase() {
    }
}
