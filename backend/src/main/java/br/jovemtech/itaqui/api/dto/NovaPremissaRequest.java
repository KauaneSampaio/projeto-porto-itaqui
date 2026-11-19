package br.jovemtech.itaqui.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NovaPremissaRequest(
        @NotBlank @Size(max = 20) @Pattern(regexp = "[0-9A-Za-z._-]+", message = "use letras, números, ponto, - ou _")
        String codigo,
        @NotBlank @Size(max = 80) String nome,
        @NotEmpty List<@Valid PercentualPeriodo> valores) {
}
