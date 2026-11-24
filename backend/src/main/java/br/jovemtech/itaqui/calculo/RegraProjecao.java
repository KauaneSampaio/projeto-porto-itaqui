package br.jovemtech.itaqui.calculo;

import java.math.BigDecimal;
import java.util.List;

/**
 * Como projetar um mês aberto a partir do orçado e das premissas vinculadas. Isolada numa
 * interface porque o método ainda depende de validação da GFin (RN004).
 */
public interface RegraProjecao {

    /** @param indices premissas vigentes da conta no mês; nunca vazio. */
    BigDecimal projetar(BigDecimal orcado, List<IndiceAplicado> indices);

    String descricao();
}
