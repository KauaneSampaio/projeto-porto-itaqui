package br.jovemtech.itaqui.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/** Novos percentuais da premissa no cenário; só meses abertos. */
public record AjustePremissaRequest(@NotEmpty List<@Valid PercentualPeriodo> ajustes) {
}
