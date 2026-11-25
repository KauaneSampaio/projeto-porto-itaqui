/* =========================================
   MAIN — rotas (troca de telas pelo endereço #) e inicialização.
   Roteador baseado no original da equipe.
========================================= */

"use strict";

let rotaAtual = null;
let pronto = false;

function nomeDaRota() {
    const nome = location.hash.replace("#", "");
    if (nome === "evidencias") {
        return "configuracoes";
    }
    return Telas[nome] ? nome : "inicial";
}

async function rotear() {
    const nome = nomeDaRota();
    const tela = Telas[nome];
    rotaAtual = nome;

    document.querySelectorAll(".view").forEach(function (v) {
        v.hidden = v.id !== "view-" + nome;
    });

    $("pageIcon").className = "fa-solid " + tela.icone;
    $("pageTitleText").textContent = tela.titulo;
    $("breadcrumb").innerHTML = nome === "inicial"
        ? '<span><i class="fa-solid fa-house"></i> Visão geral</span>'
        : '<a href="#inicial"><i class="fa-solid fa-house"></i> Visão geral</a>' +
          ' <i class="fa-solid fa-chevron-right sep"></i> <span>' + escapar(tela.titulo) + "</span>";
    document.title = tela.titulo + " | Ebitify";

    document.querySelectorAll("[data-route]").forEach(function (link) {
        const ativo = link.dataset.route === nome;
        link.classList.toggle("active", ativo);
        if (ativo) {
            link.setAttribute("aria-current", "page");
        } else {
            link.removeAttribute("aria-current");
        }
    });

    abrirMenuMobile(false);
    window.scrollTo(0, 0);
    $("pageTitle").focus({ preventScroll: true });

    if (pronto) {
        await renderizar(nome);
    }
}

async function renderizar(nome) {
    try {
        await Telas[nome].render();
    } catch (e) {
        if (e.codigo === "SEM_CONEXAO") {
            mostrarErroConexao(true);
        } else {
            mostrarAviso(mensagemErro(e), "erro");
        }
        console.error(e);
    }
}

function mostrarErroConexao(mostrar) {
    $("erroConexao").hidden = !mostrar;
    $("erroConexaoUrl").textContent = API_BASE;
}

/* Cenário ou exercício trocados no cabeçalho: redesenha a tela atual */
Estado.aoMudarContexto(function () {
    if (pronto && rotaAtual) {
        renderizar(rotaAtual);
    }
});

async function iniciar() {
    try {
        const sessao = await Api.authMe();
        if (!sessao || !sessao.autenticado) {
            location.replace("login.html");
            return;
        }
        definirCsrf(sessao.csrfToken);
        Estado.sessao = sessao;
        $("chipUsuarioTexto").textContent = sessao.nome || sessao.usuario;
        $("chipUsuario").title = "Conectado como " + sessao.usuario + " (" + sessao.perfil + ")";
        $("chipUsuario").hidden = false;
        $("btnSair").hidden = false;
        const ehAdmin = sessao.perfil === "ADMIN";
        $("statusApi").hidden = !ehAdmin;
        $("tabBtnEvidencias").hidden = !ehAdmin;
        $("tabBtnUsuarios").hidden = !ehAdmin;

        await Estado.carregar();
        mostrarErroConexao(false);
        pronto = true;
        await renderizar(nomeDaRota());
    } catch (e) {
        if (e.codigo === "NAO_AUTENTICADO") {
            return;
        }
        pronto = false;
        mostrarErroConexao(true);
        console.error(e);
    }
}

$("btnSair").addEventListener("click", async function () {
    try {
        await Api.authLogout();
    } catch (e) {
        /* mesmo se falhar, redireciona para login */
    }
    definirCsrf(null);
    location.replace("login.html");
});

window.addEventListener("hashchange", rotear);

$("btnReconectar").addEventListener("click", iniciar);

/* Inicialização */
aplicarTema(Prefs.get("tema"));
aplicarModoEscuro(Prefs.get("modoEscuro"));

$("btnModoEscuro").addEventListener("click", function () {
    const novo = !Prefs.get("modoEscuro");
    Prefs.set("modoEscuro", novo);
    aplicarModoEscuro(novo);
    if (pronto && rotaAtual) {
        renderizar(rotaAtual);
    }
});
if (Prefs.get("lembrarMenu") && Prefs.get("menuColapsado") && window.innerWidth > 850) {
    sidebar.classList.add("collapsed");
}
rotear();
iniciar();
