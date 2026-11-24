/* =========================================
   TELA: CONFIGURAÇÕES — importar base (RF002/CT-07),
   estrutura da DRE (RF001) e aparência
========================================= */

"use strict";

Telas.configuracoes = (function () {

    let abaAtual = "importar";
    const ARQUIVOS = ["config", "contas", "valores", "premissas", "vinculos"];

    /* ---------- Abas (original da equipe) ---------- */

    function selecionarAba(nome) {
        abaAtual = nome;
        document.querySelectorAll(".stab").forEach(function (b) {
            b.setAttribute("aria-selected", String(b.dataset.tab === nome));
        });
        document.querySelectorAll(".tab-panel").forEach(function (p) {
            p.hidden = p.id !== "tab-" + nome;
        });
        if (nome === "estrutura") {
            desenharEstrutura();
        }
    }

    document.querySelectorAll(".stab").forEach(function (b) {
        b.addEventListener("click", function () { selecionarAba(b.dataset.tab); });
    });

    /* ---------- Importação ---------- */

    function montarFormData() {
        const fd = new FormData();
        let faltando = null;
        document.querySelectorAll("#impForm input[type=file]").forEach(function (campo) {
            if (campo.files.length) {
                fd.append(campo.dataset.arquivo, campo.files[0]);
            } else if (!faltando) {
                faltando = campo;
            }
        });
        if (faltando) {
            faltando.reportValidity();
            return null;
        }
        return fd;
    }

    function desenharRelatorio(rel) {
        const caixa = $("impRelatorio");
        caixa.hidden = false;
        caixa.className = "relatorio " + (rel.valido ? "ok" : "falha");
        const titulo = !rel.valido ? '<i class="fa-solid fa-circle-xmark"></i> Base recusada: ' + rel.erros.length +
            " erro(s). Nada foi importado."
            : rel.aplicado ? '<i class="fa-solid fa-circle-check"></i> Base importada com sucesso.'
                : '<i class="fa-solid fa-circle-check"></i> Base válida (só validação; nada foi importado).';
        const r = rel.resumo;
        let html = "<h3>" + titulo + "</h3><p>" + r.contas + " contas · " + r.valores + " valores · " + r.premissas +
            " premissas · " + r.vinculos + " vínculos" + (r.dataCorte ? " · corte em " + escapar(rotuloPeriodo(r.dataCorte)) : "") + "</p>";
        function lista(itens) {
            return "<ul>" + itens.map(function (e) {
                return "<li><strong>" + escapar(e.arquivo) + (e.linha ? " linha " + e.linha : "") + "</strong>" +
                    (e.campo ? " (" + escapar(e.campo) + ")" : "") + ": " + escapar(e.mensagem) + "</li>";
            }).join("") + "</ul>";
        }
        if (rel.erros.length) {
            html += "<strong>Erros</strong>" + lista(rel.erros);
        }
        if (rel.avisos.length) {
            html += "<strong>Avisos</strong>" + lista(rel.avisos);
        }
        caixa.innerHTML = html;
    }

    async function enviar(simular) {
        const fd = montarFormData();
        if (!fd) {
            return;
        }
        try {
            const rel = await Api.importar(fd, simular);
            desenharRelatorio(rel);
            if (rel.aplicado) {
                Estado.invalidar();
                await Estado.carregar();
                mostrarAviso("Base importada. Todas as telas já usam os novos dados.");
                desenharSobre();
            }
        } catch (e) {
            mostrarAviso(mensagemErro(e), "erro");
        }
    }

    $("impValidar").addEventListener("click", function () { enviar(true); });

    $("impForm").addEventListener("submit", async function (evento) {
        evento.preventDefault();
        const ok = await confirmar("Importar base", "A base atual será substituída pelos arquivos enviados, se estiverem " +
            "todos válidos. Os cenários são mantidos.", "Importar");
        if (ok) {
            enviar(false);
        }
    });

    $("impModelos").innerHTML = ARQUIVOS.map(function (n) {
        return '<button type="button" class="btn btn-light" data-modelo="' + n + '"><i class="fa-solid fa-download"></i> ' +
            n + ".csv</button>";
    }).join("");

    document.querySelectorAll("[data-modelo]").forEach(function (b) {
        b.addEventListener("click", async function () {
            try {
                const texto = await Api.arquivoBase(b.dataset.modelo + ".csv");
                baixarArquivo(new Blob(["﻿" + texto], { type: "text/csv;charset=utf-8" }), b.dataset.modelo + ".csv");
            } catch (e) {
                mostrarAviso(mensagemErro(e), "erro");
            }
        });
    });

    $("impRestaurar").addEventListener("click", async function () {
        const ok = await confirmar("Restaurar base fictícia", "Importações e alterações de estrutura, premissas e " +
            "vínculos serão desfeitas. Os cenários são mantidos.", "Restaurar");
        if (!ok) {
            return;
        }
        try {
            await Api.restaurar();
            Estado.invalidar();
            await Estado.carregar();
            desenharSobre();
            $("impRelatorio").hidden = true;
            mostrarAviso("Base fictícia do projeto restaurada.");
        } catch (e) {
            mostrarAviso(mensagemErro(e), "erro");
        }
    });

    /* ---------- Estrutura da DRE ---------- */

    async function desenharEstrutura() {
        let arvore;
        try {
            arvore = await Api.estrutura();
        } catch (e) {
            mostrarAviso(mensagemErro(e), "erro");
            return;
        }
        const linhas = [];
        const grupos = [];
        (function percorrer(nos) {
            nos.forEach(function (n) {
                linhas.push(n);
                if (!n.folha) {
                    grupos.push(n);
                }
                percorrer(n.filhos);
            });
        })(arvore);

        const paiAtual = $("contaPai").value;
        $("contaPai").innerHTML = '<option value="">(nenhum — conta raiz)</option>' + grupos.map(function (g) {
            return '<option value="' + escapar(g.codigo) + '">' + escapar(g.codigo + " " + g.nome) + "</option>";
        }).join("");
        $("contaPai").value = paiAtual;

        $("contaCorpo").innerHTML = linhas.map(function (n) {
            const removivel = n.filhos.length === 0 && !n.temValores;
            return '<tr><td style="padding-left:' + (12 + n.nivel * 18) + 'px">' + (n.folha ? "" : "<strong>") +
                escapar(n.codigo + " " + n.nome) + (n.folha ? "" : "</strong>") +
                (n.opex ? ' <span class="badge badge-opex badge-mini">OPEX</span>' : "") + "</td><td>" +
                escapar(TIPOS_CONTA[n.tipo] || n.tipo) + "</td><td>" + escapar(n.premissas.join(", ") || "—") + "</td>" +
                '<td class="center">' + (removivel
                    ? '<button type="button" class="icon-btn" data-remover="' + escapar(n.codigo) + '" aria-label="Remover conta" title="Remover conta"><i class="fa-regular fa-trash-can"></i></button>'
                    : '<span class="hint" title="Contas com valores ou filhas só mudam por importação">—</span>') +
                "</td></tr>";
        }).join("");

        document.querySelectorAll("[data-remover]").forEach(function (b) {
            b.addEventListener("click", async function () {
                const ok = await confirmar("Remover conta", "A conta " + b.dataset.remover + " será removida da DRE.", "Remover");
                if (!ok) {
                    return;
                }
                try {
                    await Api.removerConta(b.dataset.remover);
                    Estado.invalidar();
                    mostrarAviso("Conta removida.");
                    desenharEstrutura();
                } catch (e) {
                    mostrarAviso(mensagemErro(e), "erro");
                }
            });
        });
    }

    $("contaTipo").addEventListener("change", function () {
        const v = this.value;
        $("contaOpex").value = v === "CUSTO" || v === "DESPESA_OPERACIONAL" ? "true" : "false";
    });

    $("contaForm").addEventListener("submit", async function (evento) {
        evento.preventDefault();
        if (!this.checkValidity()) {
            this.reportValidity();
            return;
        }
        try {
            const nova = await Api.criarConta({
                codigo: $("contaCodigo").value.trim(),
                nome: $("contaNome").value.trim(),
                codigoPai: $("contaPai").value || null,
                tipo: $("contaTipo").value,
                opex: $("contaOpex").value === "true"
            });
            Estado.invalidar();
            this.reset();
            mostrarAviso("Conta " + nova.codigo + " cadastrada. Ela entra na DRE com valores zerados até a próxima importação.");
            desenharEstrutura();
        } catch (e) {
            mostrarAviso(mensagemErro(e), "erro");
        }
    });

    /* ---------- Aparência (original da equipe) ---------- */

    $("cfgTema").addEventListener("change", function () {
        $("themePreview").setAttribute("data-theme", this.value);
    });

    $("aparForm").addEventListener("submit", function (evento) {
        evento.preventDefault();
        Prefs.set("tema", $("cfgTema").value);
        aplicarTema(Prefs.get("tema"));
        mostrarAviso("Aparência aplicada.");
    });

    $("cfgLembrar").addEventListener("change", function () {
        Prefs.set("lembrarMenu", this.checked);
        Prefs.set("menuColapsado", sidebar.classList.contains("collapsed"));
    });

    /* ---------- Painel lateral ---------- */

    function desenharSobre() {
        const m = Estado.meta;
        const fontes = { SEMENTE: "Base fictícia do projeto", GRAVADA: "Base salva no servidor",
            IMPORTACAO: "Importada", EDITADA: "Editada pela interface" };
        $("sobreBase").innerHTML =
            "<div><dt>Origem dos dados</dt><dd>" + (m.dadosFicticios
                ? '<span class="badge badge-opex">Fictícios</span>' : escapar(m.origemDados)) + "</dd></div>" +
            "<div><dt>Fonte</dt><dd>" + escapar(fontes[m.fonteBase] || m.fonteBase) + "</dd></div>" +
            "<div><dt>Versão</dt><dd>" + escapar(m.versaoBase) + "</dd></div>" +
            "<div><dt>Último mês fechado</dt><dd>" + escapar(rotuloPeriodo(m.dataCorte)) + "</dd></div>" +
            "<div><dt>Exercícios</dt><dd>" + escapar(m.anos.join(", ")) + "</dd></div>" +
            "<div><dt>Contas · premissas · vínculos</dt><dd>" + m.totalContas + " · " + m.totalPremissas + " · " +
            m.totalVinculos + "</dd></div>" +
            "<div><dt>Unidade</dt><dd>" + escapar(m.unidade) + "</dd></div>";
        $("sobreRegras").innerHTML =
            "<div><dt>EBITDA</dt><dd>" + escapar(m.formulaEbitda) + "</dd></div>" +
            "<div><dt>Projeção</dt><dd>" + escapar(m.regraProjecao) + "</dd></div>";
    }

    return {
        titulo: "Configurações",
        icone: "fa-gear",
        render: async function () {
            $("cfgTema").value = Prefs.get("tema");
            $("themePreview").setAttribute("data-theme", Prefs.get("tema"));
            $("cfgLembrar").checked = !!Prefs.get("lembrarMenu");
            desenharSobre();
            selecionarAba(abaAtual);
        }
    };
})();
