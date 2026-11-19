package br.jovemtech.itaqui.api.dto;

/** Evidência do CT-06 (RNF002): tempo do recálculo completo no servidor. */
public record DesempenhoResposta(String cenarioId, int ano, int repeticoes, double minimoMs, double mediaMs,
        double maximoMs, double metaMs, boolean dentroDaMeta) {
}
