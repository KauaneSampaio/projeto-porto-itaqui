package br.jovemtech.itaqui.calculo;

import java.math.BigDecimal;
import java.util.Map;

import br.jovemtech.itaqui.dominio.Base;
import br.jovemtech.itaqui.dominio.Conta;
import br.jovemtech.itaqui.dominio.Periodo;

/** Soma as contas folha para formar os grupos da DRE (qualquer profundidade). */
final class AgregadorDre {

    /** Preenche {@code series} com os grupos, a partir das folhas já calculadas. */
    void agregar(Base base, int ano, Map<String, Serie> series) {
        for (Conta raiz : base.raizes()) {
            agregar(base, ano, raiz, series);
        }
    }

    private Serie agregar(Base base, int ano, Conta conta, Map<String, Serie> series) {
        if (conta.folha()) {
            return series.get(conta.codigo());
        }
        Serie grupo = vazia(base, ano);
        for (Conta filha : base.filhos(conta.codigo())) {
            grupo.somar(agregar(base, ano, filha, series));
        }
        series.put(conta.codigo(), grupo);
        return grupo;
    }

    private static Serie vazia(Base base, int ano) {
        Serie s = new Serie();
        for (int i = 0; i < 12; i++) {
            s.orcado[i] = BigDecimal.ZERO;
            s.forecast[i] = BigDecimal.ZERO;
            if (base.fechado(new Periodo(ano, i + 1))) {
                s.realizado[i] = BigDecimal.ZERO;
            } else {
                s.projetado[i] = BigDecimal.ZERO;
            }
        }
        return s;
    }
}
