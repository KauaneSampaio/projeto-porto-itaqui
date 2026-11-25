/* =========================================
   TELA: DRE — conta × mês, nas perspectivas
   orçado, realizado, forecast e desvio
========================================= */

"use strict";

Telas.dre = (function () {

    let perspectiva = "forecast";
    let ultimo = null;

    const PERSPECTIVAS = {
        forecast: "Forecast: realizado nos meses fechados e projeção do cenário nos abertos.",
        orcado: "Orçamento original (não muda com cenários).",
        realizado: "Realizado contábil; só existe nos meses fechados. Total = acumulado até o corte.",
        desvio: "Forecast − orçado. Verde = favorável ao EBITDA; vermelho = desfavorável."
    };

    /* Natureza (tipo das contas folha) de cada linha, para colorir o desvio dos grupos */
    function naturezas(linhas) {
        const filhos = {};
        linhas.forEach(function (l) {
            if (l.codigoPai) {
                (filhos[l.codigoPai] = filhos[l.codigoPai] || []).push(l);
            }
        });
        const mapa = {};
        function natureza(l) {
            if (mapa[l.codigo]) {
                return mapa[l.codigo];
            }
            let n = l.tipo;
            if (l.tipo === "GRUPO") {
                const f = filhos[l.codigo] || [];
                n = f.length ? natureza(f[0]) : "GRUPO";
            }
            mapa[l.codigo] = n;
            return n;
        }
        linhas.forEach(natureza);
        return mapa;
    }

    function celula(valor, classe, titulo, casas) {
        if (valor == null) {
            return '<td class="num ' + classe + ' vazio-cel">—</td>';
        }
        return '<td class="num ' + classe + '" title="' + escapar(titulo || fmtNum(valor, 2)) + '">' +
            fmtNum(valor, casas == null ? 0 : casas) + "</td>";
    }

    function valorMes(m) {
        if (perspectiva === "desvio") {
            return m.forecast - m.orcado;
        }
        return m[perspectiva];
    }

    function valorTotal(t) {
        if (perspectiva === "desvio") {
            return t.forecast - t.orcado;
        }
        return t[perspectiva];
    }

    function linhaIndicador(rotulo, chave, d, ehMargem, extraClasse) {
        let html = '<tr class="indicador ' + (extraClasse || "") + '"><td class="col-conta">' + rotulo + "</td>";
        d.indicadoresMensais.forEach(function (im) {
            const p = im.indicadores[chave];
            const v = perspectiva === "desvio" ? p.forecast - p.orcado : p[perspectiva];
            const cls = im.status === "FECHADO" ? "mes-fechado" : "mes-aberto";
            html += celula(v, cls, null, ehMargem ? 1 : 0);
        });
        const t = d.indicadores[chave];
        const vt = perspectiva === "desvio" ? t.forecast - t.orcado : t[perspectiva];
        html += celula(vt, "col-total", null, ehMargem ? 1 : 0) + "</tr>";
        return html;
    }

    function desenhar(d) {
        const soGrupos = $("dreSoGrupos").checked;
        const natureza = naturezas(d.linhas);
        const corte = rotuloPeriodo(d.dataCorte);

        const anoCorte = Number(String(d.dataCorte).slice(0, 4));
        const trechoCorte = d.ano > anoCorte ? "" : ' · <i class="fa-solid fa-lock"></i> Fechado até ' + escapar(corte);
        $("dreLegenda").innerHTML = "Valores em <strong>" + escapar(d.unidade) + "</strong> · " +
            escapar(PERSPECTIVAS[perspectiva]) + trechoCorte +
            " · Cenário: <strong>" + escapar(d.cenarioNome) + "</strong>";

        let cab = '<thead><tr><th class="col-conta">Conta</th>';
        d.meses.forEach(function (m) {
            const fechado = m.status === "FECHADO";
            cab += '<th class="' + (fechado ? "mes-fechado" : "mes-aberto") + '">' + MESES[m.mes - 1] +
                "<small>" + (fechado ? '<i class="fa-solid fa-lock"></i> fechado' : "projetado") + "</small></th>";
        });
        cab += '<th class="col-total">' + (perspectiva === "realizado" ? "Acum." : "Total") + "<small>" + d.ano +
            "</small></th></tr></thead>";

        let corpo = "<tbody>";
        d.linhas.forEach(function (l) {
            if (soGrupos && l.folha && l.nivel > 0) {
                return;
            }
            const redutora = contaRedutora(natureza[l.codigo]);
            const recuo = 10 + l.nivel * 18;
            corpo += '<tr class="nivel-' + Math.min(l.nivel, 2) + (l.opex ? " linha-opex" : "") + '">' +
                '<td class="col-conta" style="padding-left:' + recuo + 'px"><span class="conta-nome">' +
                '<span class="codigo">' + escapar(l.codigo) + '</span><span class="texto" title="' + escapar(l.nome) + '">' +
                escapar(l.nome) + "</span>" +
                (l.opex && l.folha ? '<span class="badge badge-opex badge-mini">OPEX</span>' : "") +
                (l.folha && l.premissas.length ? '<span class="badge badge-manual badge-mini" title="Premissas vinculadas">' +
                    escapar(l.premissas.join(", ")) + "</span>" : "") +
                "</span></td>";
            l.meses.forEach(function (m) {
                const v = valorMes(m);
                let cls = m.status === "FECHADO" ? "mes-fechado" : "mes-aberto";
                if (perspectiva === "desvio") {
                    cls += " " + classeDesvio(v, redutora);
                }
                corpo += celula(v, cls);
            });
            const vt = valorTotal(l.total);
            corpo += celula(vt, "col-total " + (perspectiva === "desvio" ? classeDesvio(vt, redutora) : "")) + "</tr>";
        });
        corpo += linhaIndicador("Receita líquida", "receitaLiquida", d, false);
        corpo += linhaIndicador("EBITDA", "ebitda", d, false, "indicador-ebitda");
        corpo += linhaIndicador(perspectiva === "desvio" ? "Margem EBITDA (p.p.)" : "Margem EBITDA (%)",
            "margemEbitdaPct", d, true);
        corpo += "</tbody>";

        $("dreTabela").innerHTML = cab + corpo;
    }

    document.querySelectorAll("[data-persp]").forEach(function (botao) {
        botao.addEventListener("click", function () {
            perspectiva = botao.dataset.persp;
            document.querySelectorAll("[data-persp]").forEach(function (b) {
                b.setAttribute("aria-selected", String(b === botao));
            });
            if (ultimo) {
                desenhar(ultimo);
            }
        });
    });

    $("dreSoGrupos").addEventListener("change", function () {
        if (ultimo) {
            desenhar(ultimo);
        }
    });

    $("dreCsv").addEventListener("click", async function () {
        try {
            const blob = await Api.evidenciaCsv("contas", Estado.cenarioId, Estado.ano);
            baixarArquivo(blob, "dre_" + Estado.cenarioId + "_" + Estado.ano + ".csv");
        } catch (e) {
            mostrarAviso(mensagemErro(e), "erro");
        }
    });

    return {
        titulo: "DRE",
        icone: "fa-table-list",
        render: async function () {
            ultimo = await Estado.dre();
            desenhar(ultimo);
        }
    };
})();
