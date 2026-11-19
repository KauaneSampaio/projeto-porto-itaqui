/* =========================================
   TELA: COMPARATIVO — painel triplo, desvios por grupo
   e efeito do cenário sobre o orçamento original
========================================= */

"use strict";

Telas.comparativo = (function () {

    let ultimo = null;
    let itemSelecionado = "ind:ebitda";

    const INDICADORES = [
        ["ind:ebitda", "EBITDA", false],
        ["ind:receitaLiquida", "Receita líquida", false],
        ["ind:custos", "Custos", true],
        ["ind:despesasOperacionais", "Despesas operacionais", true]
    ];

    function preencherItens(d) {
        const contas = d.linhas.map(function (l) {
            return '<option value="conta:' + escapar(l.codigo) + '">' + " ".repeat(l.nivel * 3) +
                escapar(l.codigo + " " + l.nome) + (l.opex && l.folha ? " (OPEX)" : "") + "</option>";
        }).join("");
        $("cmpItem").innerHTML = '<optgroup label="Indicadores">' + INDICADORES.map(function (i) {
            return '<option value="' + i[0] + '">' + i[1] + "</option>";
        }).join("") + '</optgroup><optgroup label="Contas da DRE">' + contas + "</optgroup>";
        const existe = Array.prototype.some.call($("cmpItem").options, function (o) { return o.value === itemSelecionado; });
        if (!existe) {
            itemSelecionado = "ind:ebitda";
        }
        $("cmpItem").value = itemSelecionado;
    }

    function desenharGrafico(d) {
        const partes = itemSelecionado.split(":");
        let serie;
        let redutora = false;
        if (partes[0] === "ind") {
            const ind = INDICADORES.find(function (i) { return i[0] === itemSelecionado; });
            redutora = ind[2];
            serie = d.indicadoresMensais.map(function (m) { return m.indicadores[partes[1]]; });
        } else {
            const codigo = itemSelecionado.slice(6);
            const linha = d.linhas.find(function (l) { return l.codigo === codigo; });
            redutora = contaRedutora(linha.tipo === "GRUPO" ? primeiraFolha(d, linha).tipo : linha.tipo);
            serie = linha.meses;
        }
        Graficos.triplo("cmpGrafico", {
            rotulos: MESES,
            titulos: MESES_LONGOS.map(function (n) { return n + " " + d.ano; }),
            status: d.meses.map(function (m) { return m.status; }),
            orcado: serie.map(function (s) { return s.orcado; }),
            realizado: serie.map(function (s) { return s.realizado; }),
            forecast: serie.map(function (s) { return s.forecast; }),
            redutora: redutora
        });
    }

    function primeiraFolha(d, grupo) {
        const filha = d.linhas.find(function (l) { return l.codigoPai === grupo.codigo; });
        if (!filha) {
            return grupo;
        }
        return filha.tipo === "GRUPO" ? primeiraFolha(d, filha) : filha;
    }

    function desenharTabela(d) {
        let html = "<thead><tr><th>Conta / grupo</th>" +
            '<th class="num">Orçado até o corte</th><th class="num">Realizado</th><th class="num">Desvio realizado</th>' +
            '<th class="num">Orçado ano</th><th class="num">Forecast ano</th><th class="num">Forecast − orçado</th>' +
            "</tr></thead><tbody>";
        d.linhas.filter(function (l) { return l.nivel <= 1; }).forEach(function (l) {
            const natureza = l.tipo === "GRUPO" ? primeiraFolha(d, l).tipo : l.tipo;
            const redutora = contaRedutora(natureza);
            const t = l.total;
            const difReal = t.realizado != null && t.orcadoAteCorte != null ? t.realizado - t.orcadoAteCorte : null;
            const difFc = t.forecast - t.orcado;
            html += '<tr class="' + (l.opex ? "linha-opex" : "") + '">' +
                '<td style="padding-left:' + (14 + l.nivel * 18) + 'px">' + (l.nivel === 0 ? "<strong>" : "") +
                escapar(l.codigo + " " + l.nome) + (l.nivel === 0 ? "</strong>" : "") +
                (l.opex ? ' <span class="badge badge-opex badge-mini">OPEX</span>' : "") + "</td>" +
                '<td class="num">' + fmtNum(t.orcadoAteCorte, 0) + "</td>" +
                '<td class="num">' + fmtNum(t.realizado, 0) + "</td>" +
                '<td class="num ' + classeDesvio(difReal, redutora) + '">' +
                fmtPctSinal(variacaoPct(t.realizado, t.orcadoAteCorte)) + "</td>" +
                '<td class="num">' + fmtNum(t.orcado, 0) + "</td>" +
                '<td class="num"><strong>' + fmtNum(t.forecast, 0) + "</strong></td>" +
                '<td class="num ' + classeDesvio(difFc, redutora) + '">' + fmtSinal(difFc, 0) +
                " <small>(" + fmtPctSinal(variacaoPct(t.forecast, t.orcado)) + ")</small></td></tr>";
        });
        const e = d.indicadores.ebitda;
        html += '<tr class="linha-destaque"><td><strong>EBITDA</strong></td>' +
            '<td class="num">—</td><td class="num"><strong>' + fmtNum(e.realizado, 0) + "</strong></td>" +
            '<td class="num">—</td><td class="num">' + fmtNum(e.orcado, 0) + "</td>" +
            '<td class="num"><strong>' + fmtNum(e.forecast, 0) + "</strong></td>" +
            '<td class="num ' + classeDesvio(e.forecast - e.orcado, false) + '"><strong>' + fmtSinal(e.forecast - e.orcado, 0) +
            "</strong></td></tr></tbody>";
        $("cmpTabela").innerHTML = html;
    }

    async function desenharCenario() {
        const card = $("cmpCenarioCard");
        if (Estado.ehOriginal()) {
            card.hidden = true;
            return;
        }
        const c = await Api.comparativo(Estado.cenarioId, Estado.ano, "original");
        card.hidden = false;
        $("cmpCenarioTitulo").textContent = c.cenarioNome + " × orçamento original";

        const rotulos = {
            receitaLiquida: "Receita líquida", custos: "Custos", despesasOperacionais: "Despesas operacionais",
            ebitda: "EBITDA", margemEbitdaPct: "Margem EBITDA"
        };
        $("cmpCenarioIndicadores").innerHTML = c.indicadores.map(function (i) {
            const f = i.forecast;
            const margem = i.indicador === "margemEbitdaPct";
            const redutora = i.indicador === "custos" || i.indicador === "despesasOperacionais";
            const fmt = margem ? function (v) { return fmtPct(v, 2); } : function (v) { return fmtNum(v, 0); };
            return '<div class="mini bg-blue-soft"><small>' + rotulos[i.indicador] + "</small><strong>" + fmt(f.cenario) +
                '</strong><span class="delta ' + classeDesvio(f.diferenca, redutora) + '">' +
                (margem ? fmtSinal(f.diferenca, 2) + " p.p." : fmtSinal(f.diferenca, 0) + " (" + fmtPctSinal(f.diferencaPct) + ")") +
                " vs. " + fmt(f.base) + "</span></div>";
        }).join("");

        const alteradas = c.linhas.filter(function (l) { return l.alterada; });
        if (!alteradas.length) {
            $("cmpCenarioTabela").innerHTML = '<tbody><tr><td class="empty">Este cenário ainda não altera nenhuma conta ' +
                'neste exercício. Ajuste premissas em <a href="#cenarios">Premissas e cenários</a>.</td></tr></tbody>';
            return;
        }
        $("cmpCenarioTabela").innerHTML = "<thead><tr><th>Conta alterada</th>" +
            '<th class="num">Forecast original</th><th class="num">Forecast cenário</th><th class="num">Diferença</th></tr></thead><tbody>' +
            alteradas.map(function (l) {
                return '<tr class="' + (l.opex ? "linha-opex" : "") + '"><td style="padding-left:' + (14 + l.nivel * 18) + 'px">' +
                    (l.folha ? "" : "<strong>") + escapar(l.codigo + " " + l.nome) + (l.folha ? "" : "</strong>") + "</td>" +
                    '<td class="num">' + fmtNum(l.total.base, 0) + '</td><td class="num">' + fmtNum(l.total.cenario, 0) +
                    '</td><td class="num">' + fmtSinal(l.total.diferenca, 0) + " <small>(" +
                    fmtPctSinal(l.total.diferencaPct) + ")</small></td></tr>";
            }).join("") + "</tbody>";
    }

    $("cmpItem").addEventListener("change", function () {
        itemSelecionado = this.value;
        if (ultimo) {
            desenharGrafico(ultimo);
        }
    });

    return {
        titulo: "Comparativo",
        icone: "fa-chart-column",
        render: async function () {
            ultimo = await Estado.dre();
            preencherItens(ultimo);
            desenharGrafico(ultimo);
            desenharTabela(ultimo);
            await desenharCenario();
        }
    };
})();
