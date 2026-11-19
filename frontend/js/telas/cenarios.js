/* =========================================
   TELA: PREMISSAS E CENÁRIOS — criar/alternar cenários,
   editar índices dos meses abertos e vincular contas
========================================= */

"use strict";

Telas.cenarios = (function () {

    /* ---------- Diálogo de nome (novo / renomear) ---------- */

    let aoSalvarNome = null;

    function abrirDialogoNome(opcoes) {
        $("nomeTitulo").textContent = opcoes.titulo;
        $("nomeValor").value = opcoes.valor || "";
        $("nomeErro").hidden = true;
        $("nomeCopiarCampo").hidden = !opcoes.copiar;
        if (opcoes.copiar) {
            $("nomeCopiar").innerHTML = '<option value="">Nenhum (começar igual ao original)</option>' +
                Estado.cenarios.filter(function (c) { return !c.somenteLeitura; }).map(function (c) {
                    return '<option value="' + escapar(c.id) + '">' + escapar(c.nome) + "</option>";
                }).join("");
        }
        aoSalvarNome = opcoes.aoSalvar;
        $("nomeDialog").showModal();
        $("nomeValor").focus();
    }

    $("nomeCancelar").addEventListener("click", function () { $("nomeDialog").close(); });

    $("nomeForm").addEventListener("submit", async function (evento) {
        evento.preventDefault();
        const nome = $("nomeValor").value.trim();
        if (!nome) {
            $("nomeValor").reportValidity();
            return;
        }
        $("nomeOk").disabled = true;
        try {
            await aoSalvarNome(nome, $("nomeCopiar").value);
            $("nomeDialog").close();
        } catch (e) {
            $("nomeErro").textContent = mensagemErro(e);
            $("nomeErro").hidden = false;
        } finally {
            $("nomeOk").disabled = false;
        }
    });

    $("cenNovo").addEventListener("click", function () {
        abrirDialogoNome({
            titulo: "Novo cenário",
            copiar: true,
            aoSalvar: async function (nome, copiarDe) {
                const novo = await Api.criarCenario(nome, copiarDe);
                await Estado.recarregarCenarios();
                mostrarAviso("Cenário “" + novo.nome + "” criado. Agora ajuste as premissas dos meses abertos.");
                Estado.mudarContexto(novo.id, null);
            }
        });
    });

    $("cenRenomear").addEventListener("click", function () {
        const atual = Estado.cenarioAtual();
        abrirDialogoNome({
            titulo: "Renomear cenário",
            valor: atual.nome,
            copiar: false,
            aoSalvar: async function (nome) {
                await Api.renomearCenario(atual.id, nome);
                await Estado.recarregarCenarios();
                mostrarAviso("Cenário renomeado.");
                Telas.cenarios.render();
            }
        });
    });

    $("cenExcluir").addEventListener("click", async function () {
        const atual = Estado.cenarioAtual();
        const ok = await confirmar("Excluir cenário", "O cenário “" + atual.nome +
            "” e seus ajustes serão removidos. O orçamento original não é afetado.", "Excluir");
        if (!ok) {
            return;
        }
        try {
            await Api.removerCenario(atual.id);
            Estado.invalidar();
            await Estado.recarregarCenarios();
            mostrarAviso("Cenário excluído.");
            Estado.mudarContexto("original", null);
        } catch (e) {
            mostrarAviso(mensagemErro(e), "erro");
        }
    });

    /* ---------- Lista de cenários ---------- */

    function desenharLista() {
        $("cenLista").innerHTML = Estado.cenarios.map(function (c) {
            const atual = c.id === Estado.cenarioId;
            return '<li><button type="button" data-cenario="' + escapar(c.id) + '" aria-current="' + atual + '">' +
                "<strong>" + (c.somenteLeitura ? '<i class="fa-solid fa-lock"></i> ' : '<i class="fa-solid fa-flask"></i> ') +
                escapar(c.nome) + "</strong><small>" +
                (c.somenteLeitura ? "Somente leitura" : c.mesesAjustados + " mês(es) ajustado(s) em " +
                    c.premissasAjustadas + " premissa(s)") + "</small></button></li>";
        }).join("");
        document.querySelectorAll("[data-cenario]").forEach(function (b) {
            b.addEventListener("click", function () {
                Estado.mudarContexto(b.dataset.cenario, null);
            });
        });
    }

    /* ---------- Impacto do cenário no EBITDA ---------- */

    async function mostrarImpacto(tempoMs) {
        const caixa = $("cenImpacto");
        if (Estado.ehOriginal()) {
            caixa.hidden = true;
            return;
        }
        const c = await Api.comparativo(Estado.cenarioId, Estado.ano, "original");
        const e = c.indicadores.find(function (i) { return i.indicador === "ebitda"; }).forecast;
        const m = c.indicadores.find(function (i) { return i.indicador === "margemEbitdaPct"; }).forecast;
        caixa.hidden = false;
        caixa.innerHTML = '<span><small>EBITDA forecast ' + Estado.ano + "</small><br>" +
            fmtNum(e.base, 0) + ' <i class="fa-solid fa-arrow-right"></i> <strong>' + fmtNum(e.cenario, 0) + "</strong></span>" +
            '<span class="' + classeDesvio(e.diferenca, false) + '"><strong>' + fmtSinal(e.diferenca, 0) + "</strong> (" +
            fmtPctSinal(e.diferencaPct) + ")</span>" +
            "<span><small>Margem</small><br>" + fmtPct(m.base, 2) + ' <i class="fa-solid fa-arrow-right"></i> <strong>' +
            fmtPct(m.cenario, 2) + "</strong></span>" +
            '<span class="tempo">' + c.linhasAlteradas + " linha(s) da DRE alteradas" +
            (tempoMs != null ? " · recalculado em " + fmtNum(tempoMs, 0) + " ms (meta < 3 s)" : "") + "</span>";
    }

    /* ---------- Premissas do cenário ---------- */

    function mesesDoAno(premissa) {
        const corte = Estado.meta.dataCorte;
        const porPeriodo = {};
        premissa.valores.forEach(function (v) { porPeriodo[v.periodo] = v; });
        const meses = [];
        for (let m = 1; m <= 12; m++) {
            const periodo = Estado.ano + "-" + String(m).padStart(2, "0");
            meses.push(porPeriodo[periodo] || {
                periodo: periodo, status: periodo <= corte ? "FECHADO" : "ABERTO", original: 0, cenario: 0, alterado: false
            });
        }
        return meses;
    }

    function desenharPremissa(p, editavel, aberta) {
        const meses = mesesDoAno(p);
        const alterados = meses.filter(function (m) { return m.alterado; }).length;
        const abertos = meses.filter(function (m) { return m.status === "ABERTO"; }).length;

        let html = '<details class="premissa" data-premissa="' + escapar(p.codigo) + '"' + (aberta ? " open" : "") + ">" +
            '<summary><i class="fa-solid fa-chevron-right chev"></i><span class="titulo">' + escapar(p.codigo) + " — " +
            escapar(p.nome) + "</span>" +
            (alterados ? '<span class="badge badge-simulado">' + alterados + " mês(es) alterado(s)</span>" : "") +
            '<span class="chips">' + (p.contasVinculadas.length
                ? p.contasVinculadas.map(function (c) { return '<span class="chip-claro">' + escapar(c) + "</span>"; }).join("")
                : '<span class="chip-claro">sem contas vinculadas</span>') + "</span></summary>" +
            '<div class="premissa-corpo">';

        if (editavel && abertos) {
            html += '<div class="premissa-ferramentas">' +
                '<label class="field"><span>Valor para todos os meses abertos</span>' +
                '<input type="number" step="0.01" min="-99.99" max="999.99" class="todos" placeholder="Ex.: 6,00"></label>' +
                '<button type="button" class="btn btn-light btn-sm acao-preencher"><i class="fa-solid fa-fill"></i> Preencher</button>' +
                '<span class="grow"></span>' +
                (alterados ? '<button type="button" class="btn btn-light btn-sm acao-desfazer"><i class="fa-solid fa-rotate-left"></i> Voltar ao original</button>' : "") +
                '<button type="button" class="btn btn-primary btn-sm acao-salvar"><i class="fa-solid fa-calculator"></i> Aplicar e recalcular</button>' +
                "</div>";
        } else if (editavel) {
            html += '<p class="hint">Todos os meses de ' + Estado.ano + " estão fechados; o realizado não muda.</p>";
        }

        html += '<div class="grade-meses">' + meses.map(function (m) {
            const fechado = m.status === "FECHADO";
            const mes = Number(m.periodo.slice(5)) - 1;
            return '<label class="mes-premissa' + (fechado ? " fechado" : "") + (m.alterado ? " alterado" : "") + '">' +
                '<span class="rotulo"><span>' + MESES[mes] + "</span>" +
                (fechado ? '<i class="fa-solid fa-lock" title="Mês fechado: usa o realizado"></i>' : "<span>%</span>") + "</span>" +
                '<input type="number" step="0.01" min="-99.99" max="999.99" required data-periodo="' + m.periodo +
                '" data-atual="' + m.cenario + '" value="' + m.cenario + '"' + (fechado || !editavel ? " disabled" : "") +
                ' aria-label="' + escapar(p.codigo + " " + MESES_LONGOS[mes]) + '">' +
                '<span class="orig">orig.: ' + fmtNum(m.original, 2) + "%</span></label>";
        }).join("") + "</div></div></details>";
        return html;
    }

    function ligarAcoesPremissas() {
        document.querySelectorAll(".premissa").forEach(function (bloco) {
            const codigo = bloco.dataset.premissa;
            const preencher = bloco.querySelector(".acao-preencher");
            if (preencher) {
                preencher.addEventListener("click", function () {
                    const campo = bloco.querySelector(".todos");
                    if (campo.value === "" || !campo.checkValidity()) {
                        campo.reportValidity();
                        return;
                    }
                    bloco.querySelectorAll("input[data-periodo]:not(:disabled)").forEach(function (i) {
                        i.value = campo.value;
                    });
                });
            }
            const salvar = bloco.querySelector(".acao-salvar");
            if (salvar) {
                salvar.addEventListener("click", function () { salvarPremissa(bloco, codigo); });
            }
            const desfazer = bloco.querySelector(".acao-desfazer");
            if (desfazer) {
                desfazer.addEventListener("click", async function () {
                    try {
                        await Api.removerAjustes(Estado.cenarioId, codigo);
                        await aposAlterar(codigo, null);
                        mostrarAviso("Premissa " + codigo + " voltou aos valores do orçamento original.");
                    } catch (e) {
                        mostrarAviso(mensagemErro(e), "erro");
                    }
                });
            }
        });
    }

    async function salvarPremissa(bloco, codigo) {
        const ajustes = [];
        const campos = bloco.querySelectorAll("input[data-periodo]:not(:disabled)");
        for (let i = 0; i < campos.length; i++) {
            const c = campos[i];
            if (!c.checkValidity()) {
                c.reportValidity();
                return;
            }
            if (Number(c.value) !== Number(c.dataset.atual)) {
                ajustes.push({ periodo: c.dataset.periodo, percentual: Number(c.value) });
            }
        }
        if (!ajustes.length) {
            mostrarAviso("Nenhum valor foi alterado.");
            return;
        }
        const inicio = performance.now();
        try {
            await Api.ajustarPremissa(Estado.cenarioId, codigo, ajustes);
            await aposAlterar(codigo, inicio);
            mostrarAviso(ajustes.length + " mês(es) de " + codigo + " atualizados; projeções recalculadas.");
        } catch (e) {
            mostrarAviso(mensagemErro(e), "erro");
        }
    }

    /* Após qualquer alteração: limpa cache, recarrega e mede o tempo até a atualização visível (CT-06) */
    async function aposAlterar(codigoAberto, inicio) {
        Estado.invalidar();
        await Estado.recarregarCenarios();
        await desenhar(codigoAberto, inicio);
    }

    /* ---------- Vínculos ---------- */

    function folhas(nos, saida) {
        nos.forEach(function (n) {
            if (n.folha) {
                saida.push(n);
            }
            folhas(n.filhos, saida);
        });
        return saida;
    }

    function desenharVinculos(vinculos, estrutura, premissas) {
        const contas = folhas(estrutura, []);
        const contaAtual = $("vinConta").value;
        $("vinConta").innerHTML = contas.map(function (c) {
            return '<option value="' + escapar(c.codigo) + '">' + escapar(c.codigo + " " + c.nome) + "</option>";
        }).join("");
        if (contaAtual) {
            $("vinConta").value = contaAtual;
        }
        $("vinPremissa").innerHTML = premissas.map(function (p) {
            return '<option value="' + escapar(p.codigo) + '">' + escapar(p.codigo + " — " + p.nome) + "</option>";
        }).join("");
        if (!$("vinInicio").value) {
            $("vinInicio").value = Estado.ano + "-01";
        }
        $("vinCorpo").innerHTML = vinculos.length ? vinculos.map(function (v) {
            return "<tr><td>" + escapar(v.codigoConta + " " + (v.nomeConta || "")) + "</td><td>" +
                escapar(v.codigoPremissa) + "</td><td>" + escapar(rotuloPeriodo(v.vigenciaInicio)) + "</td>" +
                '<td class="center"><button type="button" class="icon-btn" data-conta="' + escapar(v.codigoConta) +
                '" data-premissa="' + escapar(v.codigoPremissa) + '" aria-label="Remover vínculo" title="Remover vínculo">' +
                '<i class="fa-regular fa-trash-can"></i></button></td></tr>';
        }).join("") : '<tr><td colspan="4" class="empty">Nenhum vínculo.</td></tr>';

        document.querySelectorAll("#vinCorpo [data-conta]").forEach(function (b) {
            b.addEventListener("click", async function () {
                const ok = await confirmar("Remover vínculo", "A conta " + b.dataset.conta + " deixará de seguir " +
                    b.dataset.premissa + " em todos os cenários. A projeção dela passará a ser igual ao orçado.", "Remover");
                if (!ok) {
                    return;
                }
                try {
                    await Api.removerVinculo(b.dataset.conta, b.dataset.premissa);
                    Estado.invalidar();
                    mostrarAviso("Vínculo removido.");
                    desenhar(null, null);
                } catch (e) {
                    mostrarAviso(mensagemErro(e), "erro");
                }
            });
        });
    }

    $("vinForm").addEventListener("submit", async function (evento) {
        evento.preventDefault();
        if (!this.checkValidity()) {
            this.reportValidity();
            return;
        }
        try {
            await Api.criarVinculo({
                codigoConta: $("vinConta").value,
                codigoPremissa: $("vinPremissa").value,
                vigenciaInicio: $("vinInicio").value
            });
            Estado.invalidar();
            mostrarAviso("Vínculo criado. A conta passa a seguir a premissa nos cenários.");
            desenhar(null, null);
        } catch (e) {
            mostrarAviso(mensagemErro(e), "erro");
        }
    });

    /* ---------- Render ---------- */

    async function desenhar(codigoAberto, inicioMedicao) {
        const cenario = Estado.cenarioAtual();
        const editavel = !cenario.somenteLeitura;
        desenharLista();

        $("cenTitulo").textContent = cenario.nome;
        $("cenSubtitulo").textContent = editavel
            ? "Altere os índices dos meses abertos de " + Estado.ano + ". Meses fechados usam o realizado."
            : "Índices considerados no orçamento de " + Estado.ano + ".";
        $("cenRenomear").hidden = !editavel;
        $("cenExcluir").hidden = !editavel;
        $("cenAvisoOriginal").hidden = editavel;

        const r = await Promise.all([
            Api.premissasCenario(cenario.id), Api.vinculos(), Api.estrutura(), Api.premissas()
        ]);
        const premissas = r[0];
        $("cenPremissas").innerHTML = premissas.map(function (p, i) {
            const aberta = codigoAberto ? p.codigo === codigoAberto : (editavel && i === 0);
            return desenharPremissa(p, editavel, aberta);
        }).join("") || '<p class="empty">Nenhuma premissa cadastrada.</p>';
        ligarAcoesPremissas();
        desenharVinculos(r[1], r[2], r[3]);
        await mostrarImpacto(inicioMedicao != null ? performance.now() - inicioMedicao : null);
    }

    return {
        titulo: "Premissas e cenários",
        icone: "fa-sliders",
        render: function () { return desenhar(null, null); }
    };
})();
