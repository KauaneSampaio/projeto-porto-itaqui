package br.jovemtech.itaqui.dominio;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Índice ou premissa (IPCA, reajuste salarial...). O percentual de cada mês é o índice
 * acumulado que o orçamento original considerou naquele mês (ex.: 4,50 = 4,50%).
 */
public record Premissa(String codigo, String nome, SortedMap<Periodo, BigDecimal> percentuais) {

    public Premissa {
        percentuais = Collections.unmodifiableSortedMap(new TreeMap<>(percentuais));
    }

    public static Premissa de(String codigo, String nome, Map<Periodo, BigDecimal> percentuais) {
        return new Premissa(codigo, nome, new TreeMap<>(percentuais));
    }

    /** Percentual do mês; zero se a premissa não tiver valor para ele. */
    public BigDecimal percentual(Periodo periodo) {
        return percentuais.getOrDefault(periodo, BigDecimal.ZERO);
    }
}
