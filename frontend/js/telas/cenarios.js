/* =========================================
   TELA: PREMISSAS E CENÁRIOS — criar/alternar simulações,
   editar índices dos meses abertos e vincular contas.

   Fluxo guiado em 3 passos:
     1. escolher/criar uma simulação (o orçamento oficial é somente leitura)
     2. ajustar os índices dos meses abertos (alterações ficam pendentes até salvar)
     3. ver o impacto no EBITDA (antes × depois)
========================================= */

"use strict";

Telas.cenarios = (function () {

    let nomesContas = {};      // código da conta → nome (para mostrar o que cada índice afeta)
    let premissasAtuais = [];  // última resposta de premissasCenario
    let originalDesbloqueado = false; // só vira true quando o gestor libera explicitamente a edição do oficial

    /* ---------- Diálogo de nome (nova simulação / renomear) ---------- */

    let aoSalvarNome = null;

    function abrirDialogoNome(opcoes) {
        $("nomeTitulo").textContent = opcoes.titulo;
        $("nomeValor").value = opcoes.valor || "";
        $("nomeErro").hidden = true;
        $("nomeCopiarCampo").hidden = !opcoes.copiar;
        if (opcoes.copiar) {
            $("nomeCopiar").innerHTML = '<option value="">Nenhuma (começar igual ao oficial)</option>' +
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

    async function novaSimulacao() {
        if (!(await podeSair())) {
            return;
        }
        abrirDialogoNome({
            titulo: "Nova simulação",
            copiar: true,
            aoSalvar: async function (nome, copiarDe) {
                const novo = await Api.criarCenario(nome, copiarDe);
                await Estado.recarregarCenarios();
                mostrarAviso("Simulação “" + novo.nome + "” criada. Agora ajuste os índices dos meses abertos.");
                Estado.mudarContexto(novo.id, null);
            }
        });
    }

    $("cenNovo").addEventListener("click", novaSimulacao);
    $("cenNovoDoOficial").addEventListener("click", novaSimulacao);

    $("cenDesbloquearOficial").addEventListener("click", async function () {
        const ok = await confirmar(
            "Liberar edição do orçamento oficial?",
            "As alterações salvas mudarão diretamente os índices e valores orçados do orçamento oficial " +
            "nos meses abertos (e ficarão registradas nos logs de auditoria). Você poderá restaurar a base padrão em Configurações.",
            "Liberar edição"
        );
        if (!ok) {
            return;
        }
        originalDesbloqueado = true;
        mostrarAviso("Edição do orçamento oficial liberada. Ajuste os índices abaixo e clique em Salvar.");
        desenhar(null);
    });

    $("cenBloquearOficial").addEventListener("click", async function () {
        if (!(await podeSair())) {
            return;
        }
        originalDesbloqueado = false;
        mostrarAviso("Orçamento oficial bloqueado novamente.");
        desenhar(null);
    });

    $("cenRenomear").addEventListener("click", function () {
        const atual = Estado.cenarioAtual();
        abrirDialogoNome({
            titulo: "Renomear simulação",
            valor: atual.nome,
            copiar: false,
            aoSalvar: async function (nome) {
                await Api.renomearCenario(atual.id, nome);
                await Estado.recarregarCenarios();
                mostrarAviso("Simulação renomeada.");
                Telas.cenarios.render();
            }
        });
    });

    $("cenExcluir").addEventListener("click", async function () {
        const atual = Estado.cenarioAtual();
        const ok = await confirmar("Excluir simulação", "A simulação “" + atual.nome +
            "” e seus ajustes serão removidos. O orçamento oficial não é afetado.", "Excluir");
        if (!ok) {
            return;
        }
        try {
            await Api.removerCenario(atual.id);
            Estado.invalidar();
            await Estado.recarregarCenarios();
            mostrarAviso("Simulação excluída.");
            Estado.mudarContexto("original", null);
        } catch (e) {
            mostrarAviso(mensagemErro(e), "erro");
        }
    });

    /* ---------- Alterações pendentes (ainda não salvas) ---------- */

    function camposAlterados() {
        return Array.prototype.filter.call(document.querySelectorAll("#cenPremissas input[data-periodo]:not(:disabled)"),
            function (c) { return c.value !== "" && Number(c.value) !== Number(c.dataset.atual); });
    }

    function atualizarPendentes() {
        const alterados = camposAlterados();
        const premissas = {};
        alterados.forEach(function (c) { premissas[c.dataset.premissa] = true; });
        const nomes = Object.keys(premissas);
        $("cenPendentes").hidden = alterados.length === 0;
        $("cenPendentesTexto").textContent = alterados.length + " mês(es) alterado(s) em " + nomes.join(", ") +
            " — ainda não salvo(s)";

        // realça cada mês alterado e mostra a diferença para o oficial
        document.querySelectorAll("#cenPremissas input[data-periodo]").forEach(function (c) {
            const cartao = c.closest(".mes-premissa");
            const pendente = c.value !== "" && Number(c.value) !== Number(c.dataset.atual);
            cartao.classList.toggle("pendente", pendente);
            const delta = cartao.querySelector(".delta");
            const dif = Number(c.value) - Number(c.dataset.original);
            if (c.value === "" || Math.abs(dif) < 0.0001) {
                delta.textContent = "igual ao oficial";
                delta.className = "delta neutro";
            } else {
                delta.textContent = (dif > 0 ? "+" : "") + fmtNum(dif, 2) + " p.p.";
                delta.className = "delta " + (dif > 0 ? "sobe" : "desce");
            }
        });
    }

    async function podeSair() {
        if (!camposAlterados().length) {
            return true;
        }
        return confirmar("Descartar alterações?", "Há índices alterados que ainda não foram salvos. " +
            "Se continuar, essas alterações serão perdidas.", "Descartar");
    }

    $("cenDescartar").addEventListener("click", function () {
        document.querySelectorAll("#cenPremissas input[data-periodo]:not(:disabled)").forEach(function (c) {
            c.value = c.dataset.atual;
        });
        atualizarPendentes();
    });

    $("cenSalvar").addEventListener("click", async function () {
        const alterados = camposAlterados();
        for (let i = 0; i < alterados.length; i++) {
            if (!alterados[i].checkValidity()) {
                alterados[i].reportValidity();
                return;
            }
        }
        const porPremissa = {};
        alterados.forEach(function (c) {
            (porPremissa[c.dataset.premissa] = porPremissa[c.dataset.premissa] || [])
                .push({ periodo: c.dataset.periodo, percentual: Number(c.value) });
        });
        const codigos = Object.keys(porPremissa);
        if (!codigos.length) {
            return;
        }
        const inicio = performance.now();
        try {
            if (Estado.ehOriginal() && originalDesbloqueado) {
                for (let i = 0; i < codigos.length; i++) {
                    await Api.atualizarPremissaOriginal(codigos[i], porPremissa[codigos[i]]);
                }
                Estado.invalidar();
                await Estado.carregar();
                await desenhar(inicio);
                mostrarAviso(alterados.length + " mês(es) atualizados diretamente no orçamento oficial.");
            } else {
                for (let i = 0; i < codigos.length; i++) {
                    await Api.ajustarPremissa(Estado.cenarioId, codigos[i], porPremissa[codigos[i]]);
                }
                await aposAlterar(inicio);
                mostrarAviso(alterados.length + " mês(es) salvos. Veja o impacto no EBITDA no topo da tela.");
            }
        } catch (e) {
            mostrarAviso(mensagemErro(e), "erro");
        }
    });

    /* ---------- Lista de simulações ---------- */

    function desenharLista() {
        $("cenLista").innerHTML = Estado.cenarios.map(function (c) {
            const atual = c.id === Estado.cenarioId;
            const detalhe = c.somenteLeitura ? "Oficial · somente leitura"
                : c.mesesAjustados ? c.mesesAjustados + " mês(es) ajustado(s) em " + c.premissasAjustadas + " índice(s)"
                    : "Ainda igual ao oficial";
            return '<li><button type="button" data-cenario="' + escapar(c.id) + '" aria-current="' + atual + '"' +
                (c.somenteLeitura ? ' class="oficial"' : "") + ">" +
                "<strong>" + (c.somenteLeitura ? '<i class="fa-solid fa-lock"></i> Orçamento oficial'
                    : '<i class="fa-solid fa-flask"></i> ' + escapar(c.nome)) + "</strong>" +
                "<small>" + detalhe + "</small></button></li>";
        }).join("");
        document.querySelectorAll("#cenLista [data-cenario]").forEach(function (b) {
            b.addEventListener("click", async function () {
                if (b.dataset.cenario !== Estado.cenarioId && await podeSair()) {
                    Estado.mudarContexto(b.dataset.cenario, null);
                }
            });
        });
    }

    /* ---------- Passo a passo ---------- */

    function desenharPassos(cenario) {
        const passo = cenario.somenteLeitura ? 1 : cenario.mesesAjustados ? 3 : 2;
        document.querySelectorAll("#cenPassos li").forEach(function (li) {
            const n = Number(li.dataset.passo);
            li.classList.toggle("feito", n < passo);
            li.classList.toggle("atual", n === passo);
        });
    }

    /* ---------- Ano sem meses abertos ---------- */

    function temMesesAbertos(ano) {
        const corte = Estado.meta.dataCorte;
        return ano + "-12" > corte;
    }

    function desenharAvisoAno() {
        const caixa = $("cenAnoFechado");
        if (temMesesAbertos(Estado.ano)) {
            caixa.hidden = true;
            return false;
        }
        const proximo = Estado.meta.anos.filter(temMesesAbertos)[0];
        caixa.hidden = false;
        caixa.innerHTML = '<i class="fa-solid fa-calendar-xmark"></i><span>Todos os meses de <strong>' + Estado.ano +
            "</strong> já estão fechados — o realizado não muda, então não há o que simular neste ano." +
            (proximo ? ' <button type="button" class="btn btn-light btn-sm" id="cenIrAno">Ir para ' + proximo + "</button>" : "") +
            "</span>";
        if (proximo) {
            $("cenIrAno").addEventListener("click", function () { Estado.mudarContexto(null, proximo); });
        }
        return true;
    }

    /* ---------- Impacto da simulação no EBITDA ---------- */

    async function mostrarImpacto(cenario, tempoMs) {
        const caixa = $("cenImpacto");
        if (cenario.somenteLeitura) {
            caixa.hidden = true;
            return;
        }
        caixa.hidden = false;
        if (!cenario.mesesAjustados) {
            caixa.innerHTML = '<div class="impacto-vazio"><i class="fa-solid fa-arrow-down"></i> ' +
                "<span>Esta simulação ainda está <strong>igual ao orçamento oficial</strong>. Altere algum índice abaixo " +
                "e clique em <strong>Salvar e recalcular</strong> para ver o impacto no EBITDA.</span></div>";
            return;
        }
        const c = await Api.comparativo(Estado.cenarioId, Estado.ano, "original");
        const e = c.indicadores.find(function (i) { return i.indicador === "ebitda"; }).forecast;
        const m = c.indicadores.find(function (i) { return i.indicador === "margemEbitdaPct"; }).forecast;
        const classe = classeDesvio(e.diferenca, false);
        caixa.innerHTML =
            '<div class="impacto-card"><small>EBITDA forecast ' + Estado.ano + " · oficial</small><strong>" +
            fmtNum(e.base, 0) + "</strong><span>margem " + fmtPct(m.base, 2) + "</span></div>" +
            '<div class="impacto-seta"><i class="fa-solid fa-arrow-right"></i></div>' +
            '<div class="impacto-card destaque"><small>Nesta simulação</small><strong>' + fmtNum(e.cenario, 0) +
            "</strong><span>margem " + fmtPct(m.cenario, 2) + "</span></div>" +
            '<div class="impacto-card ' + classe + '"><small>Diferença</small><strong>' + fmtSinal(e.diferenca, 0) +
            "</strong><span>" + fmtPctSinal(e.diferencaPct) + " no EBITDA</span></div>" +
            '<div class="impacto-rodape">' + c.linhasAlteradas + " linha(s) da DRE mudaram" +
            (tempoMs != null ? " · recalculado em " + fmtNum(tempoMs, 0) + " ms" : "") +
            ' · <a href="#comparativo">Ver conta a conta no Comparativo <i class="fa-solid fa-arrow-right"></i></a></div>';
    }

    /* ---------- Índices (premissas) ---------- */

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

    function descreverContas(codigos) {
        if (!codigos.length) {
            return '<span class="afeta vazio">Nenhuma conta segue este índice — alterá-lo não muda a DRE.</span>';
        }
        const nomes = codigos.map(function (c) { return nomesContas[c] || c; });
        const visiveis = nomes.slice(0, 3).map(function (n) { return '<span class="chip-claro">' + escapar(n) + "</span>"; });
        const resto = nomes.length - 3;
        return '<span class="afeta"><span class="afeta-rotulo">Afeta ' + codigos.length + " conta(s):</span>" +
            visiveis.join("") + (resto > 0 ? '<span class="chip-claro" title="' + escapar(nomes.slice(3).join(", ")) +
                '">+' + resto + " outras</span>" : "") + "</span>";
    }

    function cartaoMes(p, m, editavel) {
        const fechado = m.status === "FECHADO";
        const mes = Number(m.periodo.slice(5)) - 1;
        return '<label class="mes-premissa' + (fechado ? " fechado" : "") + (m.alterado ? " alterado" : "") + '">' +
            '<span class="rotulo"><span>' + MESES[mes] + "</span>" +
            (fechado ? '<i class="fa-solid fa-lock" title="Mês fechado: usa o realizado"></i>' : "") + "</span>" +
            '<span class="campo-pct"><input type="number" step="0.01" min="-99.99" max="999.99" required' +
            ' data-premissa="' + escapar(p.codigo) + '" data-periodo="' + m.periodo + '" data-atual="' + m.cenario +
            '" data-original="' + m.original + '" value="' + m.cenario + '"' + (fechado || !editavel ? " disabled" : "") +
            ' aria-label="' + escapar(p.codigo + " " + MESES_LONGOS[mes]) + '"><span>%</span></span>' +
            '<span class="orig">oficial: ' + fmtNum(m.original, 2) + "%</span>" +
            '<span class="delta neutro">igual ao oficial</span></label>';
    }

    function desenharPremissa(p, editavel, aberta) {
        const meses = mesesDoAno(p);
        const abertos = meses.filter(function (m) { return m.status === "ABERTO"; });
        const fechados = meses.length - abertos.length;
        const alterados = meses.filter(function (m) { return m.alterado; }).length;
        const mostrar = abertos.length ? abertos : meses;

        let html = '<details class="premissa" data-premissa="' + escapar(p.codigo) + '"' + (aberta ? " open" : "") + ">" +
            '<summary><i class="fa-solid fa-chevron-right chev"></i>' +
            '<span class="titulo"><strong>' + escapar(p.nome) + "</strong>" +
            (p.nome !== p.codigo ? ' <small>' + escapar(p.codigo) + "</small>" : "") + "</span>" +
            (alterados ? '<span class="badge badge-simulado">' + alterados + " mês(es) alterado(s)</span>"
                : '<span class="badge badge-neutro">igual ao oficial</span>') +
            "</summary>" +
            '<div class="premissa-corpo">' + descreverContas(p.contasVinculadas);

        if (editavel && abertos.length) {
            html += '<div class="premissa-ferramentas">' +
                '<span class="rotulo-ferramentas">Atalhos:</span>' +
                '<button type="button" class="btn btn-light btn-sm acao-somar" data-delta="0.5">+0,5 p.p.</button>' +
                '<button type="button" class="btn btn-light btn-sm acao-somar" data-delta="-0.5">−0,5 p.p.</button>' +
                '<span class="separador"></span>' +
                '<label class="field-inline">Mesmo valor em todos: ' +
                '<input type="number" step="0.01" min="-99.99" max="999.99" class="todos" placeholder="0,00"> %</label>' +
                '<button type="button" class="btn btn-light btn-sm acao-preencher"><i class="fa-solid fa-fill"></i> Aplicar</button>' +
                '<span class="grow"></span>' +
                (alterados ? '<button type="button" class="btn btn-light btn-sm acao-desfazer"><i class="fa-solid fa-rotate-left"></i> Voltar ao oficial</button>' : "") +
                "</div>";
        }

        html += '<div class="grade-meses">' + mostrar.map(function (m) { return cartaoMes(p, m, editavel); }).join("") + "</div>";
        if (abertos.length && fechados) {
            html += '<p class="meses-fechados"><i class="fa-solid fa-lock"></i> ' + MESES[0] + "–" + MESES[fechados - 1] +
                " já fechados: usam o realizado e não entram na simulação.</p>";
        }
        html += "</div></details>";
        return html;
    }

    function ligarAcoesPremissas() {
        document.querySelectorAll("#cenPremissas input[data-periodo]").forEach(function (c) {
            c.addEventListener("input", atualizarPendentes);
        });
        document.querySelectorAll(".premissa").forEach(function (bloco) {
            const codigo = bloco.dataset.premissa;
            const editaveis = function () { return bloco.querySelectorAll("input[data-periodo]:not(:disabled)"); };

            bloco.querySelectorAll(".acao-somar").forEach(function (b) {
                b.addEventListener("click", function () {
                    const d = Number(b.dataset.delta);
                    editaveis().forEach(function (i) { i.value = (Number(i.value || 0) + d).toFixed(2); });
                    atualizarPendentes();
                });
            });
            const preencher = bloco.querySelector(".acao-preencher");
            if (preencher) {
                preencher.addEventListener("click", function () {
                    const campo = bloco.querySelector(".todos");
                    if (campo.value === "" || !campo.checkValidity()) {
                        campo.reportValidity();
                        return;
                    }
                    editaveis().forEach(function (i) { i.value = campo.value; });
                    atualizarPendentes();
                });
            }
            const desfazer = bloco.querySelector(".acao-desfazer");
            if (desfazer) {
                desfazer.addEventListener("click", async function () {
                    const ok = await confirmar("Voltar ao oficial", "Todos os meses de " + codigo +
                        " voltam aos valores do orçamento oficial nesta simulação.", "Voltar ao oficial");
                    if (!ok) {
                        return;
                    }
                    try {
                        await Api.removerAjustes(Estado.cenarioId, codigo);
                        await aposAlterar(null);
                        mostrarAviso(codigo + " voltou aos valores do orçamento oficial.");
                    } catch (e) {
                        mostrarAviso(mensagemErro(e), "erro");
                    }
                });
            }
        });
    }

    /* Após qualquer alteração: limpa cache, recarrega e mede o tempo até a atualização visível (CT-06) */
    async function aposAlterar(inicio) {
        Estado.invalidar();
        await Estado.recarregarCenarios();
        await desenhar(inicio);
    }

    /* ---------- Vínculos (avançado) ---------- */

    function folhas(nos, saida) {
        nos.forEach(function (n) {
            if (n.folha) {
                saida.push(n);
            }
            folhas(n.filhos, saida);
        });
        return saida;
    }

    function desenharVinculos(vinculos, contas, premissas) {
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
                    b.dataset.premissa + " em todas as simulações. A projeção dela passará a ser igual ao orçado.", "Remover");
                if (!ok) {
                    return;
                }
                try {
                    await Api.removerVinculo(b.dataset.conta, b.dataset.premissa);
                    Estado.invalidar();
                    mostrarAviso("Vínculo removido.");
                    desenhar(null);
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
            mostrarAviso("Vínculo criado. A conta passa a seguir o índice nas simulações.");
            desenhar(null);
        } catch (e) {
            mostrarAviso(mensagemErro(e), "erro");
        }
    });

    /* ---------- Render ---------- */

    async function desenhar(inicioMedicao) {
        const cenario = Estado.cenarioAtual();
        const ehOficial = cenario.somenteLeitura;
        if (!ehOficial) {
            originalDesbloqueado = false;
        }
        const editavel = !ehOficial || originalDesbloqueado;
        desenharLista();
        desenharPassos(cenario);

        $("cenTitulo").textContent = ehOficial ? "Orçamento oficial" : cenario.nome;
        $("cenTipo").className = "badge " + (!ehOficial ? "badge-simulado" : originalDesbloqueado ? "badge-falha" : "badge-original");
        $("cenTipo").textContent = !ehOficial ? "Simulação" : originalDesbloqueado ? "Edição liberada" : "Somente leitura";
        $("cenSubtitulo").textContent = editavel
            ? (ehOficial
                ? "Editando diretamente os índices do orçamento oficial de " + Estado.ano + "."
                : "Troque os índices dos meses abertos de " + Estado.ano + " e salve para recalcular o EBITDA.")
            : "Índices usados no orçamento aprovado de " + Estado.ano + ".";
        $("cenRenomear").hidden = ehOficial;
        $("cenExcluir").hidden = ehOficial;
        $("cenBloquearOficial").hidden = !(ehOficial && originalDesbloqueado);
        $("cenAvisoOriginal").hidden = !ehOficial || originalDesbloqueado;
        $("cenAvisoEdicaoOficial").hidden = !(ehOficial && originalDesbloqueado);
        $("cenPendentes").hidden = true;
        $("cenSalvar").innerHTML = ehOficial && originalDesbloqueado
            ? '<i class="fa-solid fa-floppy-disk"></i> Salvar no orçamento oficial'
            : '<i class="fa-solid fa-calculator"></i> Salvar e recalcular';

        const simulacoes = Estado.cenarios.filter(function (c) { return !c.somenteLeitura; });
        $("cenAbrirExistente").innerHTML = !editavel && simulacoes.length
            ? "ou abra uma simulação existente na lista ao lado" : "";

        const anoFechado = desenharAvisoAno();

        const r = await Promise.all([
            Api.premissasCenario(cenario.id), Api.vinculos(), Api.estrutura(), Api.premissas()
        ]);
        const contas = folhas(r[2], []);
        nomesContas = {};
        contas.forEach(function (c) { nomesContas[c.codigo] = c.nome; });
        premissasAtuais = r[0];

        $("cenPremissasHint").innerHTML = editavel && !anoFechado
            ? "Cada índice reajusta as contas ligadas a ele. Digite o novo percentual do mês — " +
              "a projeção da conta é recalculada como <em>orçado × (1 + novo%) ÷ (1 + oficial%)</em>."
            : "Valores de referência do orçamento oficial.";

        // na simulação, todos os índices já abrem expandidos; no oficial ficam recolhidos para não poluir
        $("cenPremissas").innerHTML = premissasAtuais.map(function (p) {
            return desenharPremissa(p, editavel, editavel && !anoFechado);
        }).join("") || '<p class="empty">Nenhum índice cadastrado.</p>';
        ligarAcoesPremissas();
        atualizarPendentes();
        desenharVinculos(r[1], contas, r[3]);
        await mostrarImpacto(cenario, inicioMedicao != null ? performance.now() - inicioMedicao : null);
    }

    return {
        titulo: "Premissas e cenários",
        icone: "fa-sliders",
        render: function () { return desenhar(null); }
    };
})();
