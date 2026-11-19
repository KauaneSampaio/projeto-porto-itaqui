package br.jovemtech.itaqui.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Cenário com os ajustes de premissas que diferem do orçamento original. */
public record CenarioResposta(String id, String nome, boolean somenteLeitura, Instant criadoEm, Instant atualizadoEm,
        int premissasAjustadas, int mesesAjustados, Map<String, List<PercentualPeriodo>> ajustes) {
}
