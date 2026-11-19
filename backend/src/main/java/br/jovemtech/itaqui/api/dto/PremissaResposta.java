package br.jovemtech.itaqui.api.dto;

import java.util.List;

public record PremissaResposta(String codigo, String nome, List<PercentualPeriodo> valores,
        List<String> contasVinculadas) {
}
