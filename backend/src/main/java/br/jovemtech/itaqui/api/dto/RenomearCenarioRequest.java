package br.jovemtech.itaqui.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenomearCenarioRequest(@NotBlank @Size(max = 60) String nome) {
}
