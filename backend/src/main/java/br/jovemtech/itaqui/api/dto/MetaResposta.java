package br.jovemtech.itaqui.api.dto;

import java.time.Instant;
import java.util.List;

import br.jovemtech.itaqui.dominio.Periodo;

public record MetaResposta(
        String sistema,
        Periodo dataCorte,
        List<Integer> anos,
        String origemDados,
        boolean dadosFicticios,
        String aviso,
        String unidade,
        int versaoBase,
        String fonteBase,
        Instant baseCarregadaEm,
        int totalContas,
        int totalPremissas,
        int totalVinculos,
        int totalCenarios,
        String regraProjecao,
        String formulaEbitda) {
}
