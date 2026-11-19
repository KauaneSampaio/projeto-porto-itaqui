package br.jovemtech.itaqui.api.dto;

import java.math.BigDecimal;

import br.jovemtech.itaqui.dominio.Periodo;
import jakarta.validation.constraints.NotNull;

/** Percentual de uma premissa num mês. Ex.: {"periodo": "2026-10", "percentual": 4.5}. */
public record PercentualPeriodo(@NotNull Periodo periodo, @NotNull BigDecimal percentual) {
}
