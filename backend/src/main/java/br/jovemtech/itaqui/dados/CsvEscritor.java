package br.jovemtech.itaqui.dados;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import br.jovemtech.itaqui.dominio.Base;
import br.jovemtech.itaqui.dominio.Conta;
import br.jovemtech.itaqui.dominio.Periodo;
import br.jovemtech.itaqui.dominio.ValorLancado;

/** Grava uma base no mesmo formato que o {@link CarregadorBase} lê (decimal com vírgula). */
public final class CsvEscritor {

    public Map<String, String> escrever(Base base) {
        Map<String, String> arquivos = new LinkedHashMap<>();
        arquivos.put(ArquivosBase.CONFIG, "chave;valor\n"
                + "data_corte;" + base.dataCorte() + "\n"
                + "origem;" + base.origem() + "\n"
                + "unidade;" + campo(base.unidade()) + "\n"
                + "versao;" + base.versao() + "\n");

        StringBuilder contas = new StringBuilder("codigo;nome;codigo_pai;tipo;opex;ordem\n");
        for (Conta c : base.contas()) {
            contas.append(String.join(";", campo(c.codigo()), campo(c.nome()),
                    c.codigoPai() == null ? "" : campo(c.codigoPai()), c.tipo().name(), c.opex() ? "S" : "N",
                    String.valueOf(c.ordem()))).append('\n');
        }
        arquivos.put(ArquivosBase.CONTAS, contas.toString());

        String valores = base.valores().stream()
                .sorted(Comparator.comparing(ValorLancado::codigoConta)
                        .thenComparing(ValorLancado::periodo)
                        .thenComparing(ValorLancado::tipo))
                .map(v -> String.join(";", campo(v.codigoConta()), String.valueOf(v.periodo().ano()),
                        String.valueOf(v.periodo().mes()), v.tipo().name(), numero(v.valor())))
                .collect(Collectors.joining("\n", "codigo_conta;ano;mes;tipo;valor\n", "\n"));
        arquivos.put(ArquivosBase.VALORES, valores);

        StringBuilder premissas = new StringBuilder("codigo;nome;ano;mes;percentual\n");
        base.premissas().forEach(p -> {
            for (Map.Entry<Periodo, BigDecimal> e : p.percentuais().entrySet()) {
                premissas.append(String.join(";", campo(p.codigo()), campo(p.nome()),
                        String.valueOf(e.getKey().ano()), String.valueOf(e.getKey().mes()), numero(e.getValue())))
                        .append('\n');
            }
        });
        arquivos.put(ArquivosBase.PREMISSAS, premissas.toString());

        StringBuilder vinculos = new StringBuilder("codigo_conta;codigo_premissa;vigencia_inicio\n");
        base.vinculos().forEach(v -> vinculos.append(String.join(";", campo(v.codigoConta()),
                campo(v.codigoPremissa()), v.vigenciaInicio().toString())).append('\n'));
        arquivos.put(ArquivosBase.VINCULOS, vinculos.toString());
        return arquivos;
    }

    public static String numero(BigDecimal valor) {
        return valor.toPlainString().replace('.', ',');
    }

    /** Coloca aspas quando o texto tem ; aspas ou quebra de linha. */
    public static String campo(String texto) {
        if (texto.contains(";") || texto.contains("\"") || texto.contains("\n")) {
            return '"' + texto.replace("\"", "\"\"") + '"';
        }
        return texto;
    }
}
