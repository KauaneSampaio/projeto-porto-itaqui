package br.jovemtech.itaqui.api.dto;

import java.math.BigDecimal;
import java.util.List;

import br.jovemtech.itaqui.dominio.Periodo;
import br.jovemtech.itaqui.dominio.StatusPeriodo;

/** Premissa como o cenário a enxerga: original × cenário, mês a mês. */
public record PremissaCenarioResposta(String codigo, String nome, List<String> contasVinculadas,
        List<Valor> valores) {

    public record Valor(Periodo periodo, StatusPeriodo status, BigDecimal original, BigDecimal cenario,
            boolean alterado) {
    }
}
