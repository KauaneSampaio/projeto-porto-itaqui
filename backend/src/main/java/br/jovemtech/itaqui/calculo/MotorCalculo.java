package br.jovemtech.itaqui.calculo;

import java.math.BigDecimal;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import br.jovemtech.itaqui.calculo.ResultadoDre.IndicadoresMes;
import br.jovemtech.itaqui.calculo.ResultadoDre.LinhaDre;
import br.jovemtech.itaqui.calculo.ResultadoDre.MesInfo;
import br.jovemtech.itaqui.calculo.ResultadoDre.TotaisLinha;
import br.jovemtech.itaqui.calculo.ResultadoDre.ValoresMes;
import br.jovemtech.itaqui.dominio.Base;
import br.jovemtech.itaqui.dominio.Cenario;
import br.jovemtech.itaqui.dominio.Conta;
import br.jovemtech.itaqui.dominio.Periodo;
import br.jovemtech.itaqui.dominio.TipoConta;
import br.jovemtech.itaqui.dominio.Vinculo;

import static br.jovemtech.itaqui.calculo.Valores.r2;
import static br.jovemtech.itaqui.calculo.Valores.soma;

/**
 * Ponto de entrada do cálculo. Java puro: recebe a base e o cenário, devolve a DRE do ano.
 * Não guarda estado nem cache — recalcula tudo a cada chamada (a base é pequena).
 */
public final class MotorCalculo {

    private final RegraProjecao regra;
    private final MotorForecast forecast;
    private final AgregadorDre agregador = new AgregadorDre();
    private final CalculadoraEbitda ebitda = new CalculadoraEbitda();

    public MotorCalculo(RegraProjecao regra) {
        this.regra = regra;
        this.forecast = new MotorForecast(regra);
    }

    public RegraProjecao regra() {
        return regra;
    }

    public ResultadoDre calcular(Base base, Cenario cenario, int ano) {
        long inicio = System.nanoTime();

        Map<String, Serie> series = new HashMap<>();
        List<Map.Entry<TipoConta, Serie>> folhas = new ArrayList<>();
        for (Conta c : base.contas()) {
            if (c.folha()) {
                Serie s = forecast.calcular(base, cenario, c, ano);
                series.put(c.codigo(), s);
                folhas.add(new AbstractMap.SimpleEntry<>(c.tipo(), s));
            }
        }
        agregador.agregar(base, ano, series);

        List<MesInfo> meses = new ArrayList<>();
        boolean algumFechado = false;
        for (int m = 1; m <= 12; m++) {
            Periodo p = new Periodo(ano, m);
            meses.add(new MesInfo(m, p, base.status(p)));
            algumFechado |= base.fechado(p);
        }

        List<LinhaDre> linhas = new ArrayList<>();
        for (Conta raiz : base.raizes()) {
            montarLinhas(base, raiz, 0, series, meses, linhas);
        }

        Map<TipoConta, Serie> porTipo = CalculadoraEbitda.porTipo(folhas);
        List<IndicadoresMes> mensais = new ArrayList<>();
        for (MesInfo m : meses) {
            boolean fechado = base.fechado(m.periodo());
            mensais.add(new IndicadoresMes(m.mes(), m.status(), ebitda.doMes(porTipo, m.mes() - 1, fechado)));
        }

        double ms = (System.nanoTime() - inicio) / 1_000_000.0;
        return new ResultadoDre(cenario.id(), cenario.nome(), ano, base.dataCorte(), base.origem(), base.unidade(),
                meses, linhas, ebitda.doAno(porTipo, algumFechado), mensais, Math.round(ms * 1000) / 1000.0);
    }

    private void montarLinhas(Base base, Conta conta, int nivel, Map<String, Serie> series, List<MesInfo> meses,
            List<LinhaDre> saida) {
        Serie s = series.get(conta.codigo());
        List<ValoresMes> valores = new ArrayList<>();
        BigDecimal orc = null;
        BigDecimal orcAteCorte = null;
        BigDecimal real = null;
        BigDecimal proj = null;
        BigDecimal fc = null;
        for (MesInfo m : meses) {
            int i = m.mes() - 1;
            valores.add(new ValoresMes(m.mes(), m.status(), r2(s.orcado[i]), r2(s.realizado[i]),
                    r2(s.projetado[i]), r2(s.forecast[i])));
            orc = soma(orc, s.orcado[i]);
            if (s.realizado[i] != null) {
                orcAteCorte = soma(orcAteCorte, s.orcado[i]);
            }
            real = soma(real, s.realizado[i]);
            proj = soma(proj, s.projetado[i]);
            fc = soma(fc, s.forecast[i]);
        }
        TotaisLinha total = new TotaisLinha(r2(orc), r2(orcAteCorte), r2(real), r2(proj), r2(fc));
        saida.add(new LinhaDre(conta.codigo(), conta.nome(), conta.codigoPai(), conta.tipo(), conta.opex(), nivel,
                conta.folha(), premissas(base, conta), valores, total));
        for (Conta filha : base.filhos(conta.codigo())) {
            montarLinhas(base, filha, nivel + 1, series, meses, saida);
        }
    }

    /** Códigos das premissas vinculadas à conta (para grupos, às contas abaixo dele). */
    private static List<String> premissas(Base base, Conta conta) {
        Set<String> codigos = new LinkedHashSet<>();
        coletar(base, conta, codigos);
        return List.copyOf(codigos);
    }

    private static void coletar(Base base, Conta conta, Set<String> codigos) {
        for (Vinculo v : base.vinculos(conta.codigo())) {
            codigos.add(v.codigoPremissa());
        }
        for (Conta filha : base.filhos(conta.codigo())) {
            coletar(base, filha, codigos);
        }
    }
}
