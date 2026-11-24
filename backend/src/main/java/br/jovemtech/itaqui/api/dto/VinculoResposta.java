package br.jovemtech.itaqui.api.dto;

import br.jovemtech.itaqui.dominio.Periodo;

public record VinculoResposta(String codigoConta, String nomeConta, String codigoPremissa, String nomePremissa,
        Periodo vigenciaInicio) {
}
