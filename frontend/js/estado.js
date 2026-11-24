/* =========================================
   ESTADO — contexto global (cenário, exercício, metadados)
   e cache curto das respostas de cálculo.
========================================= */

"use strict";

const Estado = {
    meta: null,
    cenarios: [],
    cenarioId: Prefs.get("cenario") || "original",
    ano: Prefs.get("ano"),
    cache: {},
    ouvintes: [],

    cenarioAtual: function () {
        return this.cenarios.find(function (c) { return c.id === Estado.cenarioId; }) || this.cenarios[0];
    },

    ehOriginal: function () {
        return this.cenarioId === "original";
    },

    /* Chamado quando algo muda no servidor: limpa o cache de cálculos */
    invalidar: function () {
        this.cache = {};
    },

    /* DRE do cenário/ano atuais (ou dos informados), com cache até a próxima alteração */
    dre: function (cenarioId, ano) {
        const id = cenarioId || this.cenarioId;
        const a = ano || this.ano;
        const chave = "dre|" + id + "|" + a;
        if (!this.cache[chave]) {
            this.cache[chave] = Api.dre(id, a).catch(function (e) {
                delete Estado.cache[chave];
                throw e;
            });
        }
        return this.cache[chave];
    },

    aoMudarContexto: function (fn) {
        this.ouvintes.push(fn);
    },

    /* Carrega metadados e cenários e preenche os seletores do cabeçalho */
    carregar: async function () {
        const resultados = await Promise.all([Api.meta(), Api.cenarios()]);
        this.meta = resultados[0];
        this.cenarios = resultados[1];

        if (!this.cenarios.some(function (c) { return c.id === Estado.cenarioId; })) {
            this.cenarioId = "original";
        }
        const anos = this.meta.anos;
        if (anos.indexOf(this.ano) === -1) {
            const anoCorte = Number(String(this.meta.dataCorte).slice(0, 4));
            this.ano = anos.indexOf(anoCorte) !== -1 ? anoCorte : anos[anos.length - 1];
        }
        this.preencherSeletores();
        this.mostrarSelo();
    },

    /* Recarrega só a lista de cenários (após criar/renomear/excluir) */
    recarregarCenarios: async function () {
        this.cenarios = await Api.cenarios();
        if (!this.cenarios.some(function (c) { return c.id === Estado.cenarioId; })) {
            this.cenarioId = "original";
            Prefs.set("cenario", "original");
        }
        this.preencherSeletores();
    },

    preencherSeletores: function () {
        $("ctxCenario").innerHTML = this.cenarios.map(function (c) {
            return '<option value="' + escapar(c.id) + '"' + (c.id === Estado.cenarioId ? " selected" : "") + ">" +
                (c.somenteLeitura ? "🔒 " : "") + escapar(c.nome) + "</option>";
        }).join("");
        $("ctxAno").innerHTML = this.meta.anos.map(function (a) {
            return '<option value="' + a + '"' + (a === Estado.ano ? " selected" : "") + ">" + a + "</option>";
        }).join("");
        const anoCorte = Number(String(this.meta.dataCorte).slice(0, 4));
        $("ctxCorteChip").hidden = this.ano > anoCorte;
        $("ctxCorte").textContent = rotuloPeriodo(this.meta.dataCorte);
    },

    mostrarSelo: function () {
        const el = $("seloFicticio");
        if (!el) {
            return;
        }
        const ficticio = this.meta.dadosFicticios;
        el.hidden = !ficticio;
        el.title = this.meta.aviso || "Dados fictícios para demonstração";
    },

    mudarContexto: function (cenarioId, ano) {
        if (cenarioId) {
            this.cenarioId = cenarioId;
            Prefs.set("cenario", cenarioId);
        }
        if (ano) {
            this.ano = Number(ano);
            Prefs.set("ano", this.ano);
        }
        this.preencherSeletores();
        this.ouvintes.forEach(function (fn) { fn(); });
    }
};

$("ctxCenario").addEventListener("change", function () {
    Estado.mudarContexto(this.value, null);
});

$("ctxAno").addEventListener("change", function () {
    Estado.mudarContexto(null, this.value);
});
