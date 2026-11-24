package br.jovemtech.itaqui.api;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.jovemtech.itaqui.api.dto.DesempenhoResposta;
import br.jovemtech.itaqui.calculo.ResultadoDre;
import br.jovemtech.itaqui.calculo.ResultadoDre.Indicadores;
import br.jovemtech.itaqui.calculo.ResultadoDre.Perspectivas;
import br.jovemtech.itaqui.dados.CsvEscritor;
import br.jovemtech.itaqui.dominio.Cenario;
import br.jovemtech.itaqui.servico.ServicoCenario;

/**
 * Exportações para conferência com a Planilha Oráculo e evidências dos testes (RF011,
 * RNF003, CT-06). Decimal com vírgula; coluna {@code origem_dados} em todas as linhas (RF012).
 */
@RestController
@RequestMapping("/api/evidencias")
public class EvidenciaController {

    private static final Map<String, Function<Indicadores, Perspectivas>> INDICADORES = new LinkedHashMap<>();

    static {
        INDICADORES.put("receita_bruta", Indicadores::receitaBruta);
        INDICADORES.put("deducoes", Indicadores::deducoes);
        INDICADORES.put("receita_liquida", Indicadores::receitaLiquida);
        INDICADORES.put("custos", Indicadores::custos);
        INDICADORES.put("despesas_operacionais", Indicadores::despesasOperacionais);
        INDICADORES.put("ebitda", Indicadores::ebitda);
        INDICADORES.put("margem_ebitda_pct", Indicadores::margemEbitdaPct);
        INDICADORES.put("depreciacao_amortizacao", Indicadores::depreciacaoAmortizacao);
    }

    private final ServicoCenario servico;

    public EvidenciaController(ServicoCenario servico) {
        this.servico = servico;
    }

    /** Layout do esperado.csv do Oráculo: cenario;ano;mes;indicador;perspectiva;valor. */
    @GetMapping("/indicadores.csv")
    public ResponseEntity<byte[]> indicadores(@RequestParam(defaultValue = Cenario.ID_ORIGINAL) String cenario,
            @RequestParam int ano) {
        ResultadoDre r = servico.dre(cenario, ano);
        StringBuilder sb = new StringBuilder("cenario;ano;mes;indicador;perspectiva;valor;origem_dados\n");
        r.indicadoresMensais().forEach(m -> linhasIndicadores(sb, r, String.valueOf(m.mes()), m.indicadores()));
        linhasIndicadores(sb, r, "TOTAL", r.indicadores());
        return Csv.anexo("indicadores_" + cenario + "_" + ano + ".csv", sb.toString());
    }

    private static void linhasIndicadores(StringBuilder sb, ResultadoDre r, String mes, Indicadores ind) {
        INDICADORES.forEach((nome, campo) -> {
            Perspectivas p = campo.apply(ind);
            linha(sb, r, mes, nome, "orcado", p.orcado());
            linha(sb, r, mes, nome, "realizado", p.realizado());
            linha(sb, r, mes, nome, "forecast", p.forecast());
        });
    }

    private static void linha(StringBuilder sb, ResultadoDre r, String mes, String indicador, String perspectiva,
            BigDecimal valor) {
        if (valor == null) {
            return;
        }
        sb.append(String.join(";", CsvEscritor.campo(r.cenarioId()), String.valueOf(r.ano()), mes, indicador,
                perspectiva, CsvEscritor.numero(valor), r.origemDados())).append('\n');
    }

    /** DRE completa conta × mês, com status do mês e as quatro colunas de valor. */
    @GetMapping("/contas.csv")
    public ResponseEntity<byte[]> contas(@RequestParam(defaultValue = Cenario.ID_ORIGINAL) String cenario,
            @RequestParam int ano) {
        ResultadoDre r = servico.dre(cenario, ano);
        StringBuilder sb = new StringBuilder(
                "cenario;ano;mes;codigo;nome;nivel;status;orcado;realizado;projetado;forecast;origem_dados\n");
        for (ResultadoDre.LinhaDre l : r.linhas()) {
            for (ResultadoDre.ValoresMes m : l.meses()) {
                sb.append(String.join(";", CsvEscritor.campo(r.cenarioId()), String.valueOf(r.ano()),
                        String.valueOf(m.mes()), CsvEscritor.campo(l.codigo()), CsvEscritor.campo(l.nome()),
                        String.valueOf(l.nivel()), m.status().name(), num(m.orcado()), num(m.realizado()),
                        num(m.projetado()), num(m.forecast()), r.origemDados())).append('\n');
            }
        }
        return Csv.anexo("dre_" + cenario + "_" + ano + ".csv", sb.toString());
    }

    private static String num(BigDecimal v) {
        return v == null ? "" : CsvEscritor.numero(v);
    }

    /** CT-06: mede o recálculo completo N vezes; meta &lt; 3000 ms (RNF002). */
    @GetMapping("/desempenho")
    public DesempenhoResposta desempenho(@RequestParam(defaultValue = Cenario.ID_ORIGINAL) String cenario,
            @RequestParam int ano, @RequestParam(defaultValue = "20") int repeticoes) {
        return servico.desempenho(cenario, ano, repeticoes);
    }
}
