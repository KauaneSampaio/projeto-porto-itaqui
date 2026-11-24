package br.jovemtech.itaqui.calculo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import br.jovemtech.itaqui.dominio.Base;
import br.jovemtech.itaqui.dominio.Cenario;
import br.jovemtech.itaqui.dominio.Conta;
import br.jovemtech.itaqui.dominio.Periodo;
import br.jovemtech.itaqui.dominio.Premissa;
import br.jovemtech.itaqui.dominio.TipoValor;
import br.jovemtech.itaqui.dominio.Vinculo;

/** Orçado, realizado, projetado e forecast de uma conta folha (RF007, RN003, RN005). */
final class MotorForecast {

    private final RegraProjecao regra;

    MotorForecast(RegraProjecao regra) {
        this.regra = regra;
    }

    Serie calcular(Base base, Cenario cenario, Conta conta, int ano) {
        Serie s = new Serie();
        for (int i = 0; i < 12; i++) {
            Periodo p = new Periodo(ano, i + 1);
            BigDecimal orcado = zeroSeNulo(base.valor(conta.codigo(), p, TipoValor.ORCADO));
            s.orcado[i] = orcado;
            if (base.fechado(p)) {
                // Mês fechado: o realizado é a verdade e não depende de cenário.
                BigDecimal realizado = zeroSeNulo(base.valor(conta.codigo(), p, TipoValor.REALIZADO));
                s.realizado[i] = realizado;
                s.forecast[i] = realizado;
            } else {
                List<IndiceAplicado> indices = indices(base, cenario, conta, p);
                BigDecimal projetado = indices.isEmpty() ? orcado : regra.projetar(orcado, indices);
                s.projetado[i] = projetado;
                s.forecast[i] = projetado;
            }
        }
        return s;
    }

    static List<IndiceAplicado> indices(Base base, Cenario cenario, Conta conta, Periodo periodo) {
        List<IndiceAplicado> indices = new ArrayList<>();
        for (Vinculo v : base.vinculos(conta.codigo())) {
            if (!v.vigente(periodo)) {
                continue;
            }
            Premissa premissa = base.premissa(v.codigoPremissa()).orElse(null);
            if (premissa == null) {
                continue;
            }
            indices.add(new IndiceAplicado(premissa.codigo(), premissa.percentual(periodo),
                    cenario.percentual(premissa, periodo)));
        }
        return indices;
    }

    private static BigDecimal zeroSeNulo(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
