package br.jovemtech.itaqui.calculo;

import java.math.BigDecimal;

/** Premissa vinculada a uma conta num mês: percentual do orçamento original e do cenário. */
public record IndiceAplicado(String codigoPremissa, BigDecimal percentualOriginal, BigDecimal percentualCenario) {
}
