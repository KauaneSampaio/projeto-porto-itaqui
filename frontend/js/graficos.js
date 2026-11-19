/* =========================================
   GRÁFICOS — painel triplo (orçado × realizado × forecast)
   Baseado na configuração e no tooltip personalizado do
   gráfico original da equipe (Chart.js 4).
========================================= */

"use strict";

const Graficos = (function () {

    const instancias = {};

    function cor(nome, padrao) {
        const v = getComputedStyle(document.documentElement).getPropertyValue(nome).trim();
        return v || padrao;
    }

    /* Posição da caixinha: no topo do grupo de barras (original da equipe) */
    Chart.Tooltip.positioners.acimaDasBarras = function (itens) {
        if (!itens.length) {
            return false;
        }
        let topo = Infinity;
        let soma = 0;
        itens.forEach(function (i) {
            topo = Math.min(topo, i.element.y);
            soma += i.element.x;
        });
        return { x: soma / itens.length, y: topo };
    };

    /* Caixinha personalizada, adaptada para as três perspectivas */
    function tooltipExterno(contexto) {
        const chart = contexto.chart;
        const tooltip = contexto.tooltip;
        const pai = chart.canvas.parentNode;
        const extra = chart.$extra || {};

        let caixa = pai.querySelector(".chart-tip");
        if (!caixa) {
            caixa = document.createElement("div");
            caixa.className = "chart-tip";
            pai.appendChild(caixa);
        }
        if (tooltip.opacity === 0) {
            caixa.style.opacity = "0";
            return;
        }

        const i = tooltip.dataPoints[0].dataIndex;
        const orc = extra.orcado[i];
        const real = extra.realizado[i];
        const fc = extra.forecast[i];
        const fechado = extra.status[i] === "FECHADO";
        const dif = fc - orc;
        const fmt = extra.formato || function (v) { return fmtNum(v, 2); };

        caixa.innerHTML =
            '<div class="tip-title">' + escapar(extra.titulos[i]) + " · " + (fechado ? "fechado" : "projetado") + "</div>" +
            '<div class="tip-row"><i class="tip-dot" style="background:' + cor("--cor-orcado", "#9fb3cc") + '"></i><span>Orçado</span><strong>' + fmt(orc) + "</strong></div>" +
            (fechado ? '<div class="tip-row"><i class="tip-dot" style="background:' + cor("--azul", "#1d6fe0") + '"></i><span>Realizado</span><strong>' + fmt(real) + "</strong></div>" : "") +
            '<div class="tip-row"><i class="tip-dot" style="background:' + cor("--cor-forecast", "#e08a1d") + '"></i><span>Forecast</span><strong>' + fmt(fc) + "</strong></div>" +
            '<div class="tip-total"><span>Forecast − orçado</span><strong class="' +
            (classeDesvio(dif, extra.redutora) === "desfavoravel" ? "neg" : "pos") + '">' + fmtSinal(dif, 2) + "</strong></div>";

        caixa.style.opacity = "1";
        const largura = caixa.offsetWidth;
        const altura = caixa.offsetHeight;
        const passo = chart.chartArea.width / Math.max(chart.data.labels.length, 1);
        let esquerda = tooltip.caretX - largura / 2;
        let topo = tooltip.caretY - altura - 12;
        if (topo < 4) {
            topo = 8;
            esquerda = tooltip.caretX + passo * 0.4;
            if (esquerda + largura > chart.width - 4) {
                esquerda = tooltip.caretX - passo * 0.4 - largura;
            }
        }
        esquerda = Math.max(4, Math.min(esquerda, chart.width - largura - 4));
        caixa.style.left = esquerda + "px";
        caixa.style.top = topo + "px";
    }

    function eixoY(v) {
        const abs = Math.abs(v);
        if (abs >= 1e6) {
            return fmtNum(v / 1e6, 1) + " mi";
        }
        if (abs >= 1e3) {
            return fmtNum(v / 1e3, 1) + " mil";
        }
        return fmtNum(v, 0);
    }

    /*
     * dados: { rotulos, titulos, status[], orcado[], realizado[], forecast[], redutora, formato }
     * Realizado só aparece nos meses fechados; o forecast fica tracejado nos meses abertos.
     */
    function triplo(canvasId, dados) {
        Chart.defaults.font.family = "'Public Sans', 'Segoe UI', Arial, sans-serif";
        const azul = cor("--azul", "#1d6fe0");
        const laranja = cor("--cor-forecast", "#e08a1d");
        const realizado = dados.realizado.map(function (v, i) { return dados.status[i] === "FECHADO" ? v : null; });

        const config = {
            data: {
                labels: dados.rotulos,
                datasets: [
                    {
                        type: "line",
                        label: "Forecast",
                        data: dados.forecast,
                        borderColor: laranja,
                        backgroundColor: laranja,
                        borderWidth: 2.5,
                        tension: 0.25,
                        pointRadius: 4,
                        pointBackgroundColor: dados.status.map(function (s) { return s === "ABERTO" ? "#fff" : laranja; }),
                        segment: {
                            borderDash: function (ctx) {
                                return dados.status[ctx.p1DataIndex] === "ABERTO" ? [6, 5] : undefined;
                            }
                        },
                        order: 0
                    },
                    {
                        type: "bar",
                        label: "Orçado",
                        data: dados.orcado,
                        backgroundColor: cor("--cor-orcado", "#9fb3cc"),
                        borderRadius: 5,
                        barPercentage: 0.8,
                        categoryPercentage: 0.7,
                        order: 1
                    },
                    {
                        type: "bar",
                        label: "Realizado",
                        data: realizado,
                        backgroundColor: azul,
                        borderRadius: 5,
                        barPercentage: 0.8,
                        categoryPercentage: 0.7,
                        order: 1
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: { duration: reduzirMovimento ? 0 : 600, easing: "easeOutQuart" },
                interaction: { intersect: false, mode: "index" },
                plugins: {
                    legend: { display: false },
                    tooltip: { enabled: false, position: "acimaDasBarras", external: tooltipExterno }
                },
                scales: {
                    x: {
                        grid: { display: false },
                        border: { display: false },
                        ticks: { color: "#5d6c80", font: { size: 12, weight: 600 } }
                    },
                    y: {
                        beginAtZero: !dados.semZero,
                        grace: "8%",
                        border: { display: false },
                        grid: { color: "#edf1f6" },
                        ticks: { color: "#5d6c80", font: { size: 12 }, maxTicksLimit: 6, callback: eixoY }
                    }
                }
            }
        };

        if (instancias[canvasId]) {
            instancias[canvasId].destroy();
        }
        const chart = new Chart($(canvasId).getContext("2d"), config);
        chart.$extra = {
            titulos: dados.titulos, status: dados.status, orcado: dados.orcado, realizado: dados.realizado,
            forecast: dados.forecast, redutora: dados.redutora, formato: dados.formato
        };
        instancias[canvasId] = chart;
        return chart;
    }

    return { triplo: triplo };
})();
