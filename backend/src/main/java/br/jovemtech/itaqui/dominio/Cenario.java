package br.jovemtech.itaqui.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Cenário de simulação. <b>Não guarda valores</b>: guarda só os percentuais de premissas
 * que diferem do orçamento original. Tudo é recalculado a partir da base imutável, então
 * um cenário nunca altera o orçamento original nem o realizado.
 */
public record Cenario(
        String id,
        String nome,
        boolean somenteLeitura,
        Instant criadoEm,
        Instant atualizadoEm,
        Map<String, SortedMap<Periodo, BigDecimal>> ajustes) {

    public static final String ID_ORIGINAL = "original";

    public Cenario {
        Map<String, SortedMap<Periodo, BigDecimal>> copia = new LinkedHashMap<>();
        ajustes.forEach((k, v) -> copia.put(k, Collections.unmodifiableSortedMap(new TreeMap<>(v))));
        ajustes = Collections.unmodifiableMap(copia);
    }

    public static Cenario original() {
        Instant agora = Instant.EPOCH;
        return new Cenario(ID_ORIGINAL, "Orçamento original", true, agora, agora, Map.of());
    }

    /** Percentual efetivo da premissa neste cenário (ajuste, se houver; senão o original). */
    public BigDecimal percentual(Premissa premissa, Periodo periodo) {
        SortedMap<Periodo, BigDecimal> doCenario = ajustes.get(premissa.codigo());
        if (doCenario != null && doCenario.containsKey(periodo)) {
            return doCenario.get(periodo);
        }
        return premissa.percentual(periodo);
    }

    public boolean temAjuste(String codigoPremissa, Periodo periodo) {
        SortedMap<Periodo, BigDecimal> doCenario = ajustes.get(codigoPremissa);
        return doCenario != null && doCenario.containsKey(periodo);
    }

    public Cenario comAjustes(String codigoPremissa, Map<Periodo, BigDecimal> novos) {
        Map<String, SortedMap<Periodo, BigDecimal>> copia = new LinkedHashMap<>(ajustes);
        SortedMap<Periodo, BigDecimal> atual = new TreeMap<>(copia.getOrDefault(codigoPremissa, new TreeMap<>()));
        atual.putAll(novos);
        copia.put(codigoPremissa, atual);
        return new Cenario(id, nome, somenteLeitura, criadoEm, Instant.now(), copia);
    }

    public Cenario semAjustes(String codigoPremissa) {
        Map<String, SortedMap<Periodo, BigDecimal>> copia = new LinkedHashMap<>(ajustes);
        copia.remove(codigoPremissa);
        return new Cenario(id, nome, somenteLeitura, criadoEm, Instant.now(), copia);
    }

    public Cenario comNome(String novoNome) {
        return new Cenario(id, novoNome, somenteLeitura, criadoEm, Instant.now(), ajustes);
    }
}
