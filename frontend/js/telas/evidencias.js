/* =========================================
   TELA: EVIDÊNCIAS — verificação automática CT-01 a CT-07,
   exportações para a Planilha Oráculo e desempenho
========================================= */

"use strict";

Telas.evidencias = (function () {

    const TOL = 0.02; // tolerância de arredondamento (soma de valores já arredondados)

    const CASOS = [
        ["CT-01", "Cálculo base", "EBITDA = receita líquida − custos − despesas operacionais; margem = EBITDA ÷ receita líquida"],
        ["CT-02", "Forecast", "Forecast = realizado dos meses fechados + projeção dos abertos, conta a conta"],
        ["CT-03", "Recálculo por premissa", "Alterar um índice muda só as contas vinculadas, só nos meses abertos"],
        ["CT-04", "Conta sem premissa", "Conta sem vínculo mantém projeção = orçado"],
        ["CT-05", "Cenário isolado", "Simular não altera o orçamento original; o original recusa edição"],
        ["CT-06", "Desempenho", "Recálculo completo em menos de 3 segundos"],
        ["CT-07", "Integridade dos dados", "Registro inválido é recusado sem carga parcial; dados marcados como fictícios"]
    ];

    let resultados = {};

    function perto(a, b) {
        return Math.abs((a || 0) - (b || 0)) <= TOL;
    }

    function desenharResultados() {
        $("evCorpo").innerHTML = CASOS.map(function (c) {
            const r = resultados[c[0]];
            let selo = '<span class="badge badge-neutro">Não executado</span>';
            if (r) {
                selo = r.ok === true ? '<span class="badge badge-ok">Aprovado</span>'
                    : r.ok === false ? '<span class="badge badge-falha">Reprovado</span>'
                        : '<span class="badge badge-manual">Executando…</span>';
            }
            return "<tr><td><strong>" + c[0] + "</strong><br><small>" + c[1] + "</small></td><td>" + c[2] + "</td><td>" +
                selo + '</td><td class="evidencia-detalhe">' + (r ? escapar(r.evidencia || "") : "") + "</td></tr>";
        }).join("");
    }

    function registrar(id, ok, evidencia) {
        resultados[id] = { ok: ok, evidencia: evidencia };
        desenharResultados();
    }

    function anoComMesesAbertos() {
        const corte = Estado.meta.dataCorte;
        const anoCorte = Number(corte.slice(0, 4));
        const candidatos = [Estado.ano].concat(Estado.meta.anos);
        for (let i = 0; i < candidatos.length; i++) {
            const a = candidatos[i];
            if (a > anoCorte || (a === anoCorte && Number(corte.slice(5)) < 12)) {
                return a;
            }
        }
        return null;
    }

    /* ---------- Casos ---------- */

    function ct01(d) {
        const i = d.indicadores;
        const falhas = [];
        ["orcado", "realizado", "forecast"].forEach(function (p) {
            if (i.ebitda[p] == null) {
                return;
            }
            const rl = i.receitaBruta[p] - i.deducoes[p];
            const eb = rl - i.custos[p] - i.despesasOperacionais[p];
            if (!perto(rl, i.receitaLiquida[p])) {
                falhas.push("receita líquida (" + p + ")");
            }
            if (!perto(eb, i.ebitda[p])) {
                falhas.push("EBITDA (" + p + ")");
            }
            if (i.receitaLiquida[p] && Math.abs(i.ebitda[p] / i.receitaLiquida[p] * 100 - i.margemEbitdaPct[p]) > 0.01) {
                falhas.push("margem (" + p + ")");
            }
        });
        registrar("CT-01", falhas.length === 0, falhas.length ? "Divergência em: " + falhas.join(", ")
            : "EBITDA " + d.ano + " orçado " + fmtNum(i.ebitda.orcado, 2) + ", forecast " + fmtNum(i.ebitda.forecast, 2) +
            "; fórmula conferida nas 3 perspectivas. Conferência com a Planilha Oráculo: teste OraculoTest no back-end.");
    }

    function ct02(d) {
        let erros = 0;
        let verificados = 0;
        d.linhas.forEach(function (l) {
            l.meses.forEach(function (m) {
                verificados++;
                const esperado = m.status === "FECHADO" ? m.realizado : m.projetado;
                if (!perto(m.forecast, esperado)) {
                    erros++;
                }
            });
            if (!perto(l.total.forecast, (l.total.realizado || 0) + (l.total.projetado || 0))) {
                erros++;
            }
        });
        registrar("CT-02", erros === 0, verificados + " células (conta × mês) e " + d.linhas.length +
            " totais conferidos em " + d.ano + "; " + erros + " divergência(s).");
    }

    async function ct03a05(originalAntes) {
        const ano = anoComMesesAbertos();
        if (!ano) {
            registrar("CT-03", false, "Não há meses abertos na base para simular.");
            return null;
        }
        const vinculos = await Api.vinculos();
        if (!vinculos.length) {
            registrar("CT-03", false, "A base não tem vínculos conta → premissa.");
            return null;
        }
        const premissa = vinculos[0].codigoPremissa;
        const premissasCen = await Api.premissasCenario("original");
        const p = premissasCen.find(function (x) { return x.codigo === premissa; });
        const originais = {};
        p.valores.forEach(function (v) { originais[v.periodo] = Number(v.original); });
        const ajustes = [];
        for (let m = 1; m <= 12; m++) {
            const periodo = ano + "-" + String(m).padStart(2, "0");
            if (periodo > Estado.meta.dataCorte) {
                ajustes.push({ periodo: periodo, percentual: (originais[periodo] || 0) + 2 });
            }
        }

        const temp = await Api.criarCenario("Verificação automática " + new Date().toLocaleTimeString("pt-BR"));
        try {
            const inicio = performance.now();
            await Api.ajustarPremissa(temp.id, premissa, ajustes);
            const sim = await Api.dre(temp.id, ano);
            const tempoFront = performance.now() - inicio;
            const orig = await Api.dre("original", ano);

            /* CT-03 */
            let vinculadasMudaram = 0;
            let vinculadas = 0;
            let naoVinculadasIguais = 0;
            let naoVinculadas = 0;
            let fechadosIguais = true;
            sim.linhas.forEach(function (l, idx) {
                if (!l.folha) {
                    return;
                }
                const o = orig.linhas[idx];
                const segue = l.premissas.indexOf(premissa) !== -1;
                const mudou = l.meses.some(function (m, k) { return !perto(m.forecast, o.meses[k].forecast); });
                l.meses.forEach(function (m, k) {
                    if (m.status === "FECHADO" && m.forecast !== o.meses[k].forecast) {
                        fechadosIguais = false;
                    }
                });
                if (segue) {
                    vinculadas++;
                    vinculadasMudaram += mudou ? 1 : 0;
                } else {
                    naoVinculadas++;
                    naoVinculadasIguais += mudou ? 0 : 1;
                }
            });
            registrar("CT-03", vinculadasMudaram === vinculadas && naoVinculadasIguais === naoVinculadas && fechadosIguais,
                premissa + " +2 p.p. em " + ajustes.length + " mês(es) abertos de " + ano + ": " + vinculadasMudaram + "/" +
                vinculadas + " contas vinculadas mudaram; " + naoVinculadasIguais + "/" + naoVinculadas +
                " não vinculadas iguais; meses fechados " + (fechadosIguais ? "inalterados" : "ALTERADOS") + ".");

            /* CT-04 */
            let semVinculo = 0;
            let iguais = 0;
            sim.linhas.forEach(function (l) {
                if (l.folha && l.premissas.length === 0) {
                    semVinculo++;
                    const ok = l.meses.every(function (m) { return m.status === "FECHADO" || perto(m.projetado, m.orcado); });
                    iguais += ok ? 1 : 0;
                }
            });
            registrar("CT-04", semVinculo > 0 && iguais === semVinculo, semVinculo === 0
                ? "A base não tem conta sem premissa para verificar."
                : iguais + "/" + semVinculo + " contas sem premissa com projeção = orçado em todos os meses abertos de " + ano + ".");

            /* CT-05 */
            const originalDepois = await Api.dre("original", Estado.ano);
            const identico = JSON.stringify(originalAntes.linhas) === JSON.stringify(originalDepois.linhas) &&
                JSON.stringify(originalAntes.indicadores) === JSON.stringify(originalDepois.indicadores);
            let recusou = false;
            try {
                await Api.ajustarPremissa("original", premissa, ajustes.slice(0, 1));
            } catch (e) {
                recusou = e.status === 409;
            }
            registrar("CT-05", identico && recusou, "Orçamento original " + (identico ? "idêntico" : "DIFERENTE") +
                " antes e depois da simulação; tentativa de editar o original " + (recusou ? "recusada (HTTP 409)." : "NÃO foi recusada."));

            return tempoFront;
        } finally {
            await Api.removerCenario(temp.id).catch(function () { /* já removido */ });
            await Estado.recarregarCenarios();
        }
    }

    async function ct06(tempoFront) {
        const ano = anoComMesesAbertos() || Estado.ano;
        const s = await Api.desempenho("original", ano, 30);
        const ok = s.dentroDaMeta && (tempoFront == null || tempoFront < 3000);
        registrar("CT-06", ok, "Servidor: média " + fmtNum(s.mediaMs, 2) + " ms, máximo " + fmtNum(s.maximoMs, 2) +
            " ms em " + s.repeticoes + " recálculos." + (tempoFront != null
                ? " Interface: alterar premissa → DRE recalculada em " + fmtNum(tempoFront, 0) + " ms." : "") + " Meta: 3.000 ms.");
    }

    async function ct07() {
        const metaAntes = await Api.meta();
        const nomes = ["config", "contas", "valores", "premissas", "vinculos"];
        const textos = await Promise.all(nomes.map(function (n) { return Api.arquivoBase(n + ".csv"); }));
        const contaFolha = (await Api.estrutura()).reduce(function (achada, no) {
            return achada || (function busca(n) {
                if (n.folha) {
                    return n.codigo;
                }
                for (let i = 0; i < n.filhos.length; i++) {
                    const f = busca(n.filhos[i]);
                    if (f) {
                        return f;
                    }
                }
                return null;
            })(no);
        }, null);
        const anoMax = Estado.meta.anos[Estado.meta.anos.length - 1];
        const fd = new FormData();
        nomes.forEach(function (n, i) {
            let t = textos[i];
            if (n === "valores") {
                t = t.replace(/\n?$/, "\n") + contaFolha + ";" + anoMax + ";12;REALIZADO;100,00\n";
            }
            fd.append(n, new Blob([t], { type: "text/csv" }), n + ".csv");
        });
        const rel = await Api.importar(fd, true);
        const metaDepois = await Api.meta();
        const intacta = metaAntes.versaoBase === metaDepois.versaoBase && metaAntes.fonteBase === metaDepois.fonteBase &&
            metaAntes.baseCarregadaEm === metaDepois.baseCarregadaEm;
        const ok = rel.valido === false && rel.aplicado === false && intacta && metaDepois.dadosFicticios === true;
        registrar("CT-07", ok, "REALIZADO em mês aberto (" + contaFolha + " dez/" + anoMax + ") " +
            (rel.valido ? "foi ACEITO" : "recusado: “" + (rel.erros[0] ? rel.erros[0].mensagem : "") + "”") +
            "; base " + (intacta ? "intacta" : "ALTERADA") + "; origem dos dados: " + metaDepois.origemDados + ".");
    }

    async function executar() {
        const botao = $("evRodar");
        botao.disabled = true;
        resultados = {};
        CASOS.forEach(function (c) { resultados[c[0]] = { ok: null, evidencia: "" }; });
        desenharResultados();
        try {
            const originalAntes = await Api.dre("original", Estado.ano);
            ct01(originalAntes);
            ct02(originalAntes);
            const tempoFront = await ct03a05(originalAntes);
            await ct06(tempoFront);
            await ct07();
            const aprovados = Object.keys(resultados).filter(function (k) { return resultados[k].ok === true; }).length;
            const info = "Executado em " + new Date().toLocaleString("pt-BR") + " · exercício " + Estado.ano +
                " · base versão " + Estado.meta.versaoBase + " (" + Estado.meta.fonteBase + ") · dados " +
                Estado.meta.origemDados + " · " + aprovados + "/" + CASOS.length + " aprovados.";
            $("evRodape").textContent = info;
            $("evPrintInfo").textContent = info;
            mostrarAviso("Verificação concluída: " + aprovados + " de " + CASOS.length + " casos aprovados.");
        } catch (e) {
            mostrarAviso("Verificação interrompida: " + mensagemErro(e), "erro");
            $("evRodape").textContent = "Interrompida: " + mensagemErro(e);
        } finally {
            botao.disabled = false;
            Estado.invalidar();
        }
    }

    $("evRodar").addEventListener("click", executar);
    $("evImprimir").addEventListener("click", function () { window.print(); });

    async function baixar(tipo) {
        try {
            const blob = await Api.evidenciaCsv(tipo, Estado.cenarioId, Estado.ano);
            baixarArquivo(blob, tipo + "_" + Estado.cenarioId + "_" + Estado.ano + ".csv");
        } catch (e) {
            mostrarAviso(mensagemErro(e), "erro");
        }
    }

    $("evBaixarInd").addEventListener("click", function () { baixar("indicadores"); });
    $("evBaixarContas").addEventListener("click", function () { baixar("contas"); });

    $("evMedir").addEventListener("click", async function () {
        try {
            const s = await Api.desempenho(Estado.cenarioId, Estado.ano, 30);
            const inicio = performance.now();
            await Api.dre(Estado.cenarioId, Estado.ano);
            const ida = performance.now() - inicio;
            $("evDesempenho").innerHTML =
                "<div><dt>Recálculo no servidor (média)</dt><dd>" + fmtNum(s.mediaMs, 2) + " ms</dd></div>" +
                "<div><dt>Recálculo no servidor (máximo)</dt><dd>" + fmtNum(s.maximoMs, 2) + " ms</dd></div>" +
                "<div><dt>Requisição completa no navegador</dt><dd>" + fmtNum(ida, 0) + " ms</dd></div>" +
                '<div><dt>Meta de desempenho</dt><dd><span class="badge ' + (s.dentroDaMeta && ida < 3000 ? "badge-ok" : "badge-falha") +
                '">&lt; 3.000 ms</span></dd></div>';
        } catch (e) {
            mostrarAviso(mensagemErro(e), "erro");
        }
    });

    return {
        titulo: "Evidências",
        icone: "fa-clipboard-check",
        render: async function () {
            desenharResultados();
        }
    };
})();
