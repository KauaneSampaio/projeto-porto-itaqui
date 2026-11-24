package br.jovemtech.itaqui.api.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Requisição para fechamento mensal e lançamento do realizado de forma simples.
 */
public record FechamentoMesRequest(
        @NotNull
        @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "Período deve estar no formato AAAA-MM")
        String periodo,

        @NotEmpty(message = "Pelo menos um lançamento de conta deve ser informado")
        List<ItemLancamento> lancamentos
) {
    public record ItemLancamento(
            @NotNull String codigoConta,
            @NotNull BigDecimal valor
    ) {}
}
