package br.jovemtech.itaqui.api.dto;

import java.math.BigDecimal;
import java.util.List;

import br.jovemtech.itaqui.dominio.Periodo;
import br.jovemtech.itaqui.dominio.StatusPeriodo;

/** Forecast de um cenário comparado com outro (normalmente o original). */
public record ComparativoResposta(
        String cenarioId,
        String cenarioNome,
        String baseId,
        String baseNome,
        int ano,
        Periodo dataCorte,
        String origemDados,
        int linhasAlteradas,
        List<IndicadorComparado> indicadores,
        List<LinhaComparada> linhas,
        double tempoCalculoMs) {

    /** {@code diferencaPct} é nulo quando a base é zero; para a margem, a diferença já está em p.p. */
    public record Comparado(BigDecimal base, BigDecimal cenario, BigDecimal diferenca, BigDecimal diferencaPct) {
    }

    public record MesComparado(int mes, StatusPeriodo status, BigDecimal base, BigDecimal cenario,
            BigDecimal diferenca, BigDecimal diferencaPct) {
    }

    public record LinhaComparada(String codigo, String nome, int nivel, boolean folha, boolean opex,
            boolean alterada, Comparado total, List<MesComparado> meses) {
    }

    public record IndicadorComparado(String indicador, Comparado forecast) {
    }
}
