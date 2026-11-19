package br.jovemtech.itaqui.api.dto;

import br.jovemtech.itaqui.dominio.Periodo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VinculoRequest(@NotBlank String codigoConta, @NotBlank String codigoPremissa,
        @NotNull Periodo vigenciaInicio) {
}
