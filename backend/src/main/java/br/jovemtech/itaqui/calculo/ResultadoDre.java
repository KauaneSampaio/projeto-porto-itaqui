package br.jovemtech.itaqui.calculo;

import java.math.BigDecimal;
import java.util.List;

import br.jovemtech.itaqui.dominio.Periodo;
import br.jovemtech.itaqui.dominio.StatusPeriodo;
import br.jovemtech.itaqui.dominio.TipoConta;

/**
 * Saída do motor para um cenário e um ano. Valores arredondados a 2 casas (HALF_UP).
 *
 * <ul>
 *   <li>{@code realizado}: só nos meses FECHADOS (nulo nos abertos)</li>
 *   <li>{@code projetado}: só nos meses ABERTOS (nulo nos fechados)</li>
 *   <li>{@code forecast}: realizado se fechado, projetado se aberto</li>
 * </ul>
 */
public record ResultadoDre(
        String cenarioId,
        String cenarioNome,
        int ano,
        Periodo dataCorte,
        String origemDados,
        String unidade,
        List<MesInfo> meses,
        List<LinhaDre> linhas,
        Indicadores indicadores,
        List<IndicadoresMes> indicadoresMensais,
        double tempoCalculoMs) {

    public record MesInfo(int mes, Periodo periodo, StatusPeriodo status) {
    }

    public record ValoresMes(int mes, StatusPeriodo status, BigDecimal orcado, BigDecimal realizado,
            BigDecimal projetado, BigDecimal forecast) {
    }

    /** {@code orcadoAteCorte} = orçado só dos meses fechados, para comparar com o realizado. */
    public record TotaisLinha(BigDecimal orcado, BigDecimal orcadoAteCorte, BigDecimal realizado,
            BigDecimal projetado, BigDecimal forecast) {
    }

    public record LinhaDre(String codigo, String nome, String codigoPai, TipoConta tipo, boolean opex, int nivel,
            boolean folha, List<String> premissas, List<ValoresMes> meses, TotaisLinha total) {
    }

    public record Perspectivas(BigDecimal orcado, BigDecimal realizado, BigDecimal forecast) {
    }

    /** Valores com sinal positivo; o EBITDA já aplica as subtrações. Margem em % (ex.: 43,25). */
    public record Indicadores(Perspectivas receitaBruta, Perspectivas deducoes, Perspectivas receitaLiquida,
            Perspectivas custos, Perspectivas despesasOperacionais, Perspectivas ebitda,
            Perspectivas margemEbitdaPct, Perspectivas depreciacaoAmortizacao) {
    }

    public record IndicadoresMes(int mes, StatusPeriodo status, Indicadores indicadores) {
    }
}
