package br.jovemtech.itaqui.calculo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import br.jovemtech.itaqui.dominio.Base;
import br.jovemtech.itaqui.dominio.Conta;
import br.jovemtech.itaqui.dominio.Periodo;
import br.jovemtech.itaqui.dominio.Premissa;
import br.jovemtech.itaqui.dominio.TipoConta;
import br.jovemtech.itaqui.dominio.TipoValor;
import br.jovemtech.itaqui.dominio.ValorLancado;
import br.jovemtech.itaqui.dominio.Vinculo;

/**
 * Base mínima, conferível à mão. Ano 2026, corte em 2026-06 (jan–jun fechados).
 *
 * <pre>
 * conta  tipo                 orçado/mês  realizado/mês  premissa
 * R1     RECEITA_BRUTA        1000        1100           TARIFA (6,0%)
 * D1     DEDUCAO_RECEITA       100         110           —
 * C1     CUSTO                 300         310           IPCA (4,5%)
 * C2     CUSTO                  50          55           —
 * E1     DESPESA_OPERACIONAL   200         190           —
 * DA     DEPRECIACAO_AMORT.     40          40           —
 *
 * EBITDA orçado/mês    = 1000 − 100 − 300 − 50 − 200 = 350   (ano: 4200)
 * EBITDA realizado/mês = 1100 − 110 − 310 − 55 − 190 = 435   (jan–jun: 2610)
 * Forecast original    = 2610 + 6 × 350 = 4710
 * Margem orçada        = 350 / 900 = 38,89%
 * </pre>
 */
final class BaseTeste {

    static final int ANO = 2026;
    static final Periodo CORTE = new Periodo(2026, 6);

    private BaseTeste() {
    }

    static Base criar() {
        return criar(new Periodo(2026, 1));
    }

    /** @param vigenciaIpca mês a partir do qual C1 segue o IPCA */
    static Base criar(Periodo vigenciaIpca) {
        List<Conta> contas = List.of(
                new Conta("R", "Receita", null, TipoConta.GRUPO, false, 1),
                new Conta("R1", "Tarifas", "R", TipoConta.RECEITA_BRUTA, false, 2),
                new Conta("D1", "Impostos", null, TipoConta.DEDUCAO_RECEITA, false, 3),
                new Conta("C", "Custos", null, TipoConta.GRUPO, true, 4),
                new Conta("C1", "Manutenção", "C", TipoConta.CUSTO, true, 5),
                new Conta("C2", "Materiais", "C", TipoConta.CUSTO, true, 6),
                new Conta("E1", "Despesas gerais", null, TipoConta.DESPESA_OPERACIONAL, true, 7),
                new Conta("DA", "Depreciação", null, TipoConta.DEPRECIACAO_AMORTIZACAO, false, 8));
        Map<String, int[]> mensal = new HashMap<>();
        mensal.put("R1", new int[] { 1000, 1100 });
        mensal.put("D1", new int[] { 100, 110 });
        mensal.put("C1", new int[] { 300, 310 });
        mensal.put("C2", new int[] { 50, 55 });
        mensal.put("E1", new int[] { 200, 190 });
        mensal.put("DA", new int[] { 40, 40 });
        List<ValorLancado> valores = new ArrayList<>();
        Map<Periodo, BigDecimal> ipca = new HashMap<>();
        Map<Periodo, BigDecimal> tarifa = new HashMap<>();
        for (int m = 1; m <= 12; m++) {
            Periodo p = new Periodo(ANO, m);
            for (Map.Entry<String, int[]> e : mensal.entrySet()) {
                valores.add(new ValorLancado(e.getKey(), p, TipoValor.ORCADO, BigDecimal.valueOf(e.getValue()[0])));
                if (m <= 6) {
                    valores.add(new ValorLancado(e.getKey(), p, TipoValor.REALIZADO,
                            BigDecimal.valueOf(e.getValue()[1])));
                }
            }
            ipca.put(p, new BigDecimal("4.5"));
            tarifa.put(p, new BigDecimal("6.0"));
        }
        List<Premissa> premissas = List.of(Premissa.de("IPCA", "IPCA", ipca), Premissa.de("TARIFA", "Tarifa", tarifa));
        List<Vinculo> vinculos = List.of(new Vinculo("C1", "IPCA", vigenciaIpca),
                new Vinculo("R1", "TARIFA", new Periodo(2026, 1)));
        return new Base(contas, valores, premissas, vinculos, CORTE, Base.ORIGEM_FICTICIO, "R$", 1, "TESTE",
                Instant.now());
    }

    static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
