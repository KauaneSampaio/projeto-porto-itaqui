package br.jovemtech.itaqui.api.dto;

import br.jovemtech.itaqui.dominio.TipoConta;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NovaContaRequest(
        @NotBlank @Size(max = 20) @Pattern(regexp = "[0-9A-Za-z._-]+", message = "use letras, números, ponto, - ou _")
        String codigo,
        @NotBlank @Size(max = 80) String nome,
        String codigoPai,
        @NotNull TipoConta tipo,
        Boolean opex,
        Integer ordem) {
}
