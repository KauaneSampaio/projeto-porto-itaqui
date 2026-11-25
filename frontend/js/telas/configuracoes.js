/* =========================================
   TELA: CONFIGURAÇÕES — importar base (RF002/CT-07),
   estrutura da DRE (RF001) e aparência
========================================= */

"use strict";

Telas.configuracoes = (function () {

    let abaAtual = "fechamento";
    const ARQUIVOS = ["config", "contas", "valores", "premissas", "vinculos"];
    let contasEstrutura = [];

    /* ---------- Abas (original da equipe) ---------- */

    function selecionarAba(nome) {
        const ehAdmin = Estado.sessao && Estado.sessao.perfil === "ADMIN";
        if ((nome === "evidencias" || nome === "usuarios") && !ehAdmin) {
            nome = "fechamento";
        }
        abaAtual = nome;
        document.querySelectorAll(".stab").forEach(function (b) {
            b.setAttribute("aria-selected", String(b.dataset.tab === nome));
        });
        document.querySelectorAll(".tab-panel").forEach(function (p) {
            p.hidden = p.id !== "tab-" + nome;
        });
        if (nome === "estrutura") {
            desenharEstrutura();
        } else if (nome === "fechamento") {
            inicializarFechamento();
        } else if (nome === "logs") {
            carregarLogs();
        } else if (nome === "usuarios") {
            carregarUsuarios();
        } else if (nome === "evidencias") {
            if (Telas.evidencias && Telas.evidencias.render) {
                Telas.evidencias.render();
            }
        }
    }

    document.querySelectorAll(".stab").forEach(function (b) {
        b.addEventListener("click", function () { selecionarAba(b.dataset.tab); });
    });

    /* ---------- Fechamento Mensal / Lançamento Simplificado ---------- */

    async function inicializarFechamento() {
        if (!Estado.meta) return;
        const corte = Estado.meta.dataCorte; // ex: 2026-08
        if (corte && !$("fechamentoPeriodo").value) {
            // Próximo mês padrão
            const partes = corte.split("-").map(Number);
            let ano = partes[0];
            let mes = partes[1] + 1;
            if (mes > 12) {
                ano += 1;
                mes = 1;
            }
            $("fechamentoPeriodo").value = ano + "-" + String(mes).padStart(2, "0");
        }
        await carregarTabelaFechamento();
    }

    async function carregarTabelaFechamento() {
        try {
            const est = await Api.estrutura();
            contasEstrutura = [];
            (function coletarFolhas(nos) {
                nos.forEach(function (n) {
                    if (n.folha) {
                        contasEstrutura.push(n);
                    }
                    coletarFolhas(n.filhos || []);
                });
            })(est);
            desenharTabelaFechamento();
        } catch (e) {
            mostrarAviso(mensagemErro(e), "erro");
        }
    }

    function desenharTabelaFechamento() {
        const corpo = $("fechamentoCorpo");
        if (!corpo) return;
        const termo = ($("fechamentoFiltro").value || "").toLowerCase().trim();

        const filtradas = contasEstrutura.filter(function (c) {
            if (!termo) return true;
            return c.codigo.toLowerCase().includes(termo) || c.nome.toLowerCase().includes(termo);
        });

        if (filtradas.length === 0) {
            corpo.innerHTML = '<tr><td colspan="4" class="center">Nenhuma conta encontrada com o filtro.</td></tr>';
            return;
        }

        corpo.innerHTML = filtradas.map(function (c) {
            const rotulos = {
                RECEITA_BRUTA: "Receita",
                DEDUCAO_RECEITA: "Dedução",
                CUSTO: "Custo",
                DESPESA_OPERACIONAL: "Despesa",
                DEPRECIACAO_AMORTIZACAO: "Deprec./Amort."
            };
            return '<tr data-codigo="' + escapar(c.codigo) + '">' +
                '<td><code>' + escapar(c.codigo) + '</code></td>' +
                '<td>' + escapar(c.nome) + '</td>' +
                '<td><small>' + (rotulos[c.tipo] || c.tipo) + '</small></td>' +
                '<td class="right">' +
                    '<input type="number" step="0.01" class="input input-sm campo-realizado right" ' +
                    'data-codigo="' + escapar(c.codigo) + '" placeholder="0,00" style="max-width: 180px; display: inline-block;">' +
                '</td>' +
            '</tr>';
        }).join("");
    }

    $("fechamentoFiltro").addEventListener("input", desenharTabelaFechamento);

    $("fechamentoSugerirOrcado").addEventListener("click", async function () {
        const periodo = $("fechamentoPeriodo").value;
        if (!periodo) {
            mostrarAviso("Selecione o mês primeiro.", "erro");
            return;
        }
        const ano = Number(periodo.split("-")[0]);
        const mesIdx = Number(periodo.split("-")[1]) - 1;

        try {
            const dreData = await Api.dre("original", ano);
            const mapaOrcado = {};
            dreData.linhas.forEach(function (l) {
                if (l.meses && l.meses[mesIdx]) {
                    mapaOrcado[l.codigo] = l.meses[mesIdx].orcado;
                }
            });

            document.querySelectorAll(".campo-realizado").forEach(function (inp) {
                const cod = inp.dataset.codigo;
                if (mapaOrcado[cod] !== undefined) {
                    inp.value = mapaOrcado[cod].toFixed(2);
                }
            });
            mostrarAviso("Valores orçados carregados como sugestão de preenchimento.");
        } catch (e) {
            mostrarAviso("Não foi possível obter valores orçados: " + mensagemErro(e), "erro");
        }
    });

    $("fechamentoForm").addEventListener("submit", async function (e) {
        e.preventDefault();
        const periodo = $("fechamentoPeriodo").value;
        if (!periodo) {
            mostrarAviso("Informe o mês a fechar.", "erro");
            return;
        }

        const confirmou = await confirmar(
            "Confirmar Fechamento Mensal",
            "Deseja fechar o mês de " + rotuloPeriodo(periodo) + "? O realizado lançado substituirá as projeções e avançará a data de corte."
        );
        if (!confirmou) return;

        const lancamentos = [];
        document.querySelectorAll(".campo-realizado").forEach(function (inp) {
            if (inp.value !== "" && !isNaN(Number(inp.value))) {
                lancamentos.push({
                    codigoConta: inp.dataset.codigo,
                    valor: Number(inp.value)
                });
            }
        });
        if (!lancamentos.length) {
            mostrarAviso("Preencha o realizado de pelo menos uma conta (ou use “Preencher com Orçado”).", "erro");
            return;
        }

        try {
            await Api.fechamento({
                periodo: periodo,
                lancamentos: lancamentos
            });
            Estado.invalidar();
            await Estado.carregar();
            desenharSobre();
            mostrarAviso("Mês de " + rotuloPeriodo(periodo) + " fechado com sucesso!");
            selecionarAba("fechamento");
        } catch (err) {
            mostrarAviso(mensagemErro(err), "erro");
        }
    });

    /* ---------- Logs de Auditoria ---------- */

    async function carregarLogs() {
        const corpo = $("logCorpo");
        if (!corpo) return;
        corpo.innerHTML = '<tr><td colspan="5" class="center">Carregando logs...</td></tr>';
        try {
            const logs = await Api.logs();
            if (!logs || logs.length === 0) {
                corpo.innerHTML = '<tr><td colspan="5" class="center">Nenhum log registrado ainda.</td></tr>';
                return;
            }
            corpo.innerHTML = logs.map(function (l) {
                const data = new Date(l.dataHora);
                const dataFmt = isNaN(data.getTime()) ? l.dataHora : data.toLocaleString("pt-BR");
                const badges = {
                    CRIAR_CENARIO: "badge-ok",
                    RENOMEAR_CENARIO: "badge-neutro",
                    REMOVER_CENARIO: "badge-falha",
                    AJUSTAR_PREMISSA: "badge-simulado",
                    RESETAR_PREMISSA: "badge-neutro",
                    FECHAMENTO_MENSAL: "badge-opex",
                    EDITAR_ORCAMENTO_ORIGINAL: "badge-falha",
                    LOGIN: "badge-ok",
                    LOGOUT: "badge-neutro",
                    LOGIN_FALHA: "badge-falha",
                    LOGIN_BLOQUEADO: "badge-falha",
                    ALTERAR_SENHA: "badge-simulado",
                    CRIAR_USUARIO: "badge-ok",
                    ATUALIZAR_USUARIO: "badge-simulado",
                    REMOVER_USUARIO: "badge-falha"
                };
                const badge = badges[l.operacao] || "badge-neutro";
                return '<tr>' +
                    '<td><small>' + escapar(dataFmt) + '</small></td>' +
                    '<td><i class="fa-solid fa-user-circle"></i> ' + escapar(l.usuario || "sistema") + '</td>' +
                    '<td><span class="badge ' + badge + '">' + escapar(l.operacao) + '</span></td>' +
                    '<td>' + escapar(l.entidade) + '</td>' +
                    '<td>' + escapar(l.detalhes) + '</td>' +
                '</tr>';
            }).join("");
        } catch (e) {
            corpo.innerHTML = '<tr><td colspan="5" class="center erro">' + escapar(mensagemErro(e)) + '</td></tr>';
        }
    }

    $("logRecarregar").addEventListener("click", carregarLogs);

    /* ---------- Segurança / Alterar Senha ---------- */

    $("senhaForm").addEventListener("submit", async function (evento) {
        evento.preventDefault();
        if (!this.checkValidity()) {
            this.reportValidity();
            return;
        }
        const atual = $("senhaAtual").value;
        const nova = $("senhaNova").value;
        const conf = $("senhaConfirmar").value;
        if (nova !== conf) {
            mostrarAviso("A confirmação da nova senha não confere.", "erro");
            $("senhaConfirmar").focus();
            return;
        }
        try {
            await Api.alterarSenha(atual, nova);
            this.reset();
            mostrarAviso("Senha alterada com sucesso.");
        } catch (e) {
            mostrarAviso(mensagemErro(e), "erro");
        }
    });

    /* ---------- Gestão de Usuários (somente ADMIN) ---------- */

    async function carregarUsuarios() {
        const corpo = $("usrCorpo");
        if (!corpo) return;
        corpo.innerHTML = '<tr><td colspan="5" class="center">Carregando usuários…</td></tr>';
        try {
            const lista = await Api.listarUsuarios();
            const meuLogin = Estado.sessao && Estado.sessao.usuario;
            if (!lista || !lista.length) {
                corpo.innerHTML = '<tr><td colspan="5" class="center">Nenhum usuário cadastrado.</td></tr>';
                return;
            }
            corpo.innerHTML = lista.map(function (u) {
                const ehEu = u.login === meuLogin;
                const badgePerfil = u.perfil === "ADMIN" ? "badge-opex" : "badge-neutro";
                const badgeStatus = u.ativo ? "badge-ok" : "badge-falha";
                const acoes = ehEu
                    ? '<span class="hint">Você</span>'
                    : '<button type="button" class="icon-btn acao-status" data-login="' + escapar(u.login) +
                        '" data-ativo="' + String(!u.ativo) + '" title="' + (u.ativo ? "Desativar conta" : "Reativar conta") + '">' +
                        '<i class="fa-solid ' + (u.ativo ? "fa-user-slash" : "fa-user-check") + '"></i></button> ' +
                      '<button type="button" class="icon-btn acao-excluir-usr" data-login="' + escapar(u.login) +
                        '" title="Excluir conta"><i class="fa-regular fa-trash-can"></i></button>';
                return "<tr>" +
                    "<td><code>" + escapar(u.login) + "</code></td>" +
                    "<td>" + escapar(u.nome) + "</td>" +
                    '<td><span class="badge ' + badgePerfil + '">' + escapar(u.perfil) + "</span></td>" +
                    '<td><span class="badge ' + badgeStatus + '">' + (u.ativo ? "Ativo" : "Inativo") + "</span></td>" +
                    '<td class="center">' + acoes + "</td>" +
                "</tr>";
            }).join("");

            corpo.querySelectorAll(".acao-status").forEach(function (b) {
                b.addEventListener("click", async function () {
                    const novoAtivo = b.dataset.ativo === "true";
                    const ok = await confirmar(
                        novoAtivo ? "Reativar conta" : "Desativar conta",
                        (novoAtivo ? "Reativar" : "Desativar") + " o acesso do usuário “" + b.dataset.login + "”?"
                    );
                    if (!ok) return;
                    try {
                        await Api.alterarStatusUsuario(b.dataset.login, novoAtivo);
                        mostrarAviso("Conta " + b.dataset.login + (novoAtivo ? " reativada." : " desativada."));
                        carregarUsuarios();
                    } catch (e) {
                        mostrarAviso(mensagemErro(e), "erro");
                    }
                });
            });

            corpo.querySelectorAll(".acao-excluir-usr").forEach(function (b) {
                b.addEventListener("click", async function () {
                    const ok = await confirmar(
                        "Excluir conta",
                        "Excluir permanentemente o usuário “" + b.dataset.login + "”? Ele perderá o acesso ao sistema.",
                        "Excluir"
                    );
                    if (!ok) return;
                    try {
                        await Api.removerUsuario(b.dataset.login);
                        mostrarAviso("Usuário " + b.dataset.login + " excluído.");
                        carregarUsuarios();
                    } catch (e) {
                        mostrarAviso(mensagemErro(e), "erro");
                    }
                });
            });
        } catch (e) {
            corpo.innerHTML = '<tr><td colspan="5" class="center erro">' + escapar(mensagemErro(e)) + "</td></tr>";
        }
    }

    $("usuarioForm").addEventListener("submit", async function (evento) {
        evento.preventDefault();
        if (!this.checkValidity()) {
            this.reportValidity();
            return;
        }
        try {
            const criado = await Api.criarUsuario({
                login: $("usrLogin").value.trim(),
                nome: $("usrNome").value.trim(),
                perfil: $("usrPerfil").value,
                senha: $("usrSenha").value
            });
            this.reset();
            mostrarAviso("Conta “" + criado.login + "” criada com sucesso.");
            carregarUsuarios();
        } catch (e) {
            mostrarAviso(mensagemErro(e), "erro");
        }
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

    $("cfgModoEscuro").addEventListener("change", function () {
        Prefs.set("modoEscuro", this.checked);
        aplicarModoEscuro(this.checked);
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
            $("cfgModoEscuro").checked = !!Prefs.get("modoEscuro");
            $("cfgLembrar").checked = !!Prefs.get("lembrarMenu");
            desenharSobre();
            selecionarAba(abaAtual);
        }
    };
})();
