/* =========================================
   TELA: VISÃO GERAL — EBITDA e margem do exercício
========================================= */

"use strict";

Telas.inicial = {
    titulo: "Visão geral",
    icone: "fa-house",

    render: async function () {
        const d = await Estado.dre();
        const ind = d.indicadores;
        const cenario = Estado.cenarioAtual();
        const fechados = d.meses.filter(function (m) { return m.status === "FECHADO"; }).length;

        $("igResumo").textContent = "Exercício " + d.ano + " · " + fechados + " de 12 meses fechados (corte em " +
            rotuloPeriodo(d.dataCorte) + ") · valores em " + d.unidade + ".";
        const badge = $("igCenarioBadge");
        badge.className = "badge " + (cenario.somenteLeitura ? "badge-original" : "badge-simulado");
        badge.textContent = cenario.somenteLeitura ? "Orçamento original" : "Cenário simulado: " + cenario.nome;

        const e = ind.ebitda;
        animarNumero($("igEbitdaForecast"), e.forecast, function (v) { return fmtValor(v); });
        animarNumero($("igEbitdaOrcado"), e.orcado, function (v) { return fmtValor(v); });

        const dif = e.forecast - e.orcado;
        const favoravel = dif >= 0;
        $("igDesvioCard").className = "stat " + (favoravel ? "stat-income" : "stat-expense");
        $("igDesvio").textContent = fmtSinal(dif, 0);
        $("igDesvioPct").innerHTML = '<i class="fa-solid ' + (favoravel ? "fa-arrow-trend-up" : "fa-arrow-trend-down") +
            '"></i> ' + escapar(fmtPctSinal(variacaoPct(e.forecast, e.orcado))) + " sobre o orçado";

        const m = ind.margemEbitdaPct;
        $("igMargem").textContent = fmtPct(m.forecast);
        const pp = m.forecast != null && m.orcado != null ? m.forecast - m.orcado : null;
        $("igMargemFoot").textContent = "Orçada: " + fmtPct(m.orcado) + " (" +
            (pp == null ? "—" : fmtSinal(pp, 1) + " p.p.") + ")";

        Graficos.triplo("igGrafico", {
            rotulos: MESES,
            titulos: MESES_LONGOS.map(function (n) { return n + " " + d.ano; }),
            status: d.meses.map(function (x) { return x.status; }),
            orcado: d.indicadoresMensais.map(function (x) { return x.indicadores.ebitda.orcado; }),
            realizado: d.indicadoresMensais.map(function (x) { return x.indicadores.ebitda.realizado; }),
            forecast: d.indicadoresMensais.map(function (x) { return x.indicadores.ebitda.forecast; }),
            redutora: false
        });

        const linhas = [
            ["Receita bruta", ind.receitaBruta],
            ["(−) Deduções", ind.deducoes],
            ["Receita líquida", ind.receitaLiquida, true],
            ["(−) Custos", ind.custos],
            ["(−) Despesas operacionais", ind.despesasOperacionais],
            ["EBITDA", ind.ebitda, true],
            ["Margem EBITDA", ind.margemEbitdaPct, true, true]
        ];
        $("igUnidade").textContent = "Exercício " + d.ano + " · " + d.unidade;
        $("igResumoDre").innerHTML = linhas.map(function (l) {
            const f = l[3] ? function (v) { return fmtPct(v, 2); } : function (v) { return fmtNum(v, 0); };
            const p = l[1];
            return "<tr" + (l[2] ? ' class="linha-destaque"' : "") + "><td>" + (l[2] ? "<strong>" + l[0] + "</strong>" : l[0]) +
                '</td><td class="num">' + f(p.orcado) + '</td><td class="num">' + f(p.realizado) +
                '</td><td class="num"><strong>' + f(p.forecast) + "</strong></td></tr>";
        }).join("");
    }
};
