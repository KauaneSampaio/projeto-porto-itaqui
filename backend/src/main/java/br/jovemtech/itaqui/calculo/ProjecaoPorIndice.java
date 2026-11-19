package br.jovemtech.itaqui.calculo;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/**
 * Regra padrão: o orçado já embute o índice original; o cenário troca esse índice pelo novo.
 *
 * <pre>projetado = orçado × (1 + índice_cenário/100) / (1 + índice_original/100)</pre>
 *
 * Com mais de uma premissa vinculada, os fatores se multiplicam. No cenário original o
 * fator é 1, então projetado = orçado. Em Excel: {@code =ORCADO*(1+NOVO/100)/(1+ORIG/100)}.
 */
public final class ProjecaoPorIndice implements RegraProjecao {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    @Override
    public BigDecimal projetar(BigDecimal orcado, List<IndiceAplicado> indices) {
        BigDecimal resultado = orcado;
        for (IndiceAplicado i : indices) {
            if (i.percentualCenario().compareTo(i.percentualOriginal()) == 0) {
                continue;
            }
            BigDecimal novo = BigDecimal.ONE.add(i.percentualCenario().divide(CEM, MC), MC);
            BigDecimal antigo = BigDecimal.ONE.add(i.percentualOriginal().divide(CEM, MC), MC);
            resultado = resultado.multiply(novo, MC).divide(antigo, MC);
        }
        return resultado;
    }

    @Override
    public String descricao() {
        return "projetado = orcado × (1 + indice_cenario/100) / (1 + indice_original/100); "
                + "conta sem premissa vinculada: projetado = orcado";
    }
}
