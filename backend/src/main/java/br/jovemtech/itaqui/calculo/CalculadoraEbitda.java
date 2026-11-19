package br.jovemtech.itaqui.calculo;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

import br.jovemtech.itaqui.calculo.ResultadoDre.Indicadores;
import br.jovemtech.itaqui.calculo.ResultadoDre.Perspectivas;
import br.jovemtech.itaqui.dominio.TipoConta;

import static br.jovemtech.itaqui.calculo.Valores.percentual;
import static br.jovemtech.itaqui.calculo.Valores.r2;
import static br.jovemtech.itaqui.calculo.Valores.soma;
import static br.jovemtech.itaqui.calculo.Valores.subtrai;

/**
 * ÚNICO lugar com a fórmula do EBITDA (RN001). Se a GFin mudar a regra, muda aqui.
 *
 * <pre>
 * Receita Líquida = Receita Bruta − Deduções
 * EBITDA          = Receita Líquida − Custos − Despesas Operacionais   (D&amp;A fica fora)
 * Margem EBITDA   = EBITDA / Receita Líquida × 100                     (RN002)
 * </pre>
 */
final class CalculadoraEbitda {

    /** Valor de um componente nas três perspectivas, em precisão cheia. */
    private record Tri(BigDecimal orcado, BigDecimal realizado, BigDecimal forecast) {
    }

    /** Soma as séries das contas folha por tipo. */
    static Map<TipoConta, Serie> porTipo(Iterable<Map.Entry<TipoConta, Serie>> folhas) {
        Map<TipoConta, Serie> mapa = new EnumMap<>(TipoConta.class);
        for (Map.Entry<TipoConta, Serie> e : folhas) {
            mapa.computeIfAbsent(e.getKey(), k -> new Serie()).somar(e.getValue());
        }
        return mapa;
    }

    Indicadores doMes(Map<TipoConta, Serie> porTipo, int indiceMes, boolean fechado) {
        return calcular(tipo -> {
            Serie s = porTipo.get(tipo);
            if (s == null) {
                return new Tri(BigDecimal.ZERO, fechado ? BigDecimal.ZERO : null, BigDecimal.ZERO);
            }
            return new Tri(s.orcado[indiceMes], s.realizado[indiceMes], s.forecast[indiceMes]);
        });
    }

    /** Total do ano. Realizado = acumulado dos meses fechados (nulo se nenhum mês fechou). */
    Indicadores doAno(Map<TipoConta, Serie> porTipo, boolean algumFechado) {
        return calcular(tipo -> {
            Serie s = porTipo.get(tipo);
            if (s == null) {
                return new Tri(BigDecimal.ZERO, algumFechado ? BigDecimal.ZERO : null, BigDecimal.ZERO);
            }
            BigDecimal o = null;
            BigDecimal r = null;
            BigDecimal f = null;
            for (int i = 0; i < 12; i++) {
                o = soma(o, s.orcado[i]);
                r = soma(r, s.realizado[i]);
                f = soma(f, s.forecast[i]);
            }
            return new Tri(o, r, f);
        });
    }

    private Indicadores calcular(Function<TipoConta, Tri> componente) {
        Tri rb = componente.apply(TipoConta.RECEITA_BRUTA);
        Tri ded = componente.apply(TipoConta.DEDUCAO_RECEITA);
        Tri cu = componente.apply(TipoConta.CUSTO);
        Tri de = componente.apply(TipoConta.DESPESA_OPERACIONAL);
        Tri da = componente.apply(TipoConta.DEPRECIACAO_AMORTIZACAO);

        Tri rl = new Tri(subtrai(rb.orcado(), ded.orcado()), subtrai(rb.realizado(), ded.realizado()),
                subtrai(rb.forecast(), ded.forecast()));
        Tri ebitda = new Tri(
                subtrai(subtrai(rl.orcado(), cu.orcado()), de.orcado()),
                subtrai(subtrai(rl.realizado(), cu.realizado()), de.realizado()),
                subtrai(subtrai(rl.forecast(), cu.forecast()), de.forecast()));
        Tri margem = new Tri(percentual(ebitda.orcado(), rl.orcado()),
                percentual(ebitda.realizado(), rl.realizado()), percentual(ebitda.forecast(), rl.forecast()));

        return new Indicadores(p(rb), p(ded), p(rl), p(cu), p(de), p(ebitda), p(margem), p(da));
    }

    private static Perspectivas p(Tri t) {
        return new Perspectivas(r2(t.orcado()), r2(t.realizado()), r2(t.forecast()));
    }
}
