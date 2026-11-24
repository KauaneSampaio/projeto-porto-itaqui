package br.jovemtech.itaqui.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code copiarDe}: id de um cenário cujos ajustes serão copiados (opcional). */
public record NovoCenarioRequest(@NotBlank @Size(max = 60) String nome, String copiarDe) {
}
