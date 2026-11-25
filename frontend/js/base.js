/* =========================================
   BASE — utilitários compartilhados
   Extraídos do script.js original da equipe (formatação, avisos,
   confirmação, tema, menu, download, animação de números).
========================================= */

"use strict";

const MESES = ["Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez"];

const MESES_LONGOS = [
    "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
];

const TIPOS_CONTA = {
    GRUPO: "Grupo",
    RECEITA_BRUTA: "Receita bruta",
    DEDUCAO_RECEITA: "Dedução da receita",
    CUSTO: "Custo",
    DESPESA_OPERACIONAL: "Despesa operacional",
    DEPRECIACAO_AMORTIZACAO: "Depreciação e amortização"
};

const TEMAS = ["azul", "verde", "grafite", "indigo"];

function $(id) {
    return document.getElementById(id);
}

function escapar(texto) {
    const div = document.createElement("div");
    div.textContent = texto == null ? "" : String(texto);
    return div.innerHTML;
}

function normalizar(texto) {
    return String(texto || "")
        .toLowerCase()
        .normalize("NFD")
        .replace(/[̀-ͯ]/g, "");
}

/* ---------- Números ---------- */

function fmtNum(valor, casas) {
    if (valor == null || isNaN(valor)) {
        return "—";
    }
    const c = casas == null ? 0 : casas;
    return Number(valor).toLocaleString("pt-BR", { minimumFractionDigits: c, maximumFractionDigits: c });
}

/* Valor monetário na unidade da base (ex.: "R$ 228.435 mil") */
function fmtValor(valor, casas) {
    if (valor == null || isNaN(valor)) {
        return "—";
    }
    const unidade = (Estado.meta && Estado.meta.unidade) || "R$";
    const sufixo = unidade.replace(/^R\$\s*/, "");
    return "R$ " + fmtNum(valor, casas) + (sufixo ? " " + sufixo : "");
}

/* Valor com sinal explícito: +1.234 / −1.234 */
function fmtSinal(valor, casas) {
    if (valor == null || isNaN(valor)) {
        return "—";
    }
    const n = Number(valor);
    const txt = fmtNum(Math.abs(n), casas);
    return n > 0 ? "+" + txt : n < 0 ? "−" + txt : txt;
}

function fmtPct(valor, casas) {
    if (valor == null || isNaN(valor)) {
        return "—";
    }
    return fmtNum(valor, casas == null ? 1 : casas) + "%";
}

function fmtPctSinal(valor) {
    if (valor == null || isNaN(valor)) {
        return "—";
    }
    return fmtSinal(valor, 1) + "%";
}

/* Variação percentual (b → a); nulo quando a base é zero */
function variacaoPct(atual, base) {
    if (atual == null || base == null || Number(base) === 0) {
        return null;
    }
    return ((atual - base) / Math.abs(base)) * 100;
}

/* "2026-09" -> "set/2026" */
function rotuloPeriodo(periodo) {
    const p = String(periodo || "").split("-");
    if (p.length !== 2) {
        return "—";
    }
    return MESES[Number(p[1]) - 1].toLowerCase() + "/" + p[0];
}

/* Tipos de conta que reduzem o EBITDA: desvio positivo é desfavorável */
function contaRedutora(tipo) {
    return tipo === "DEDUCAO_RECEITA" || tipo === "CUSTO" ||
        tipo === "DESPESA_OPERACIONAL" || tipo === "DEPRECIACAO_AMORTIZACAO";
}

/* Classe de cor para um desvio, considerando a natureza da conta */
function classeDesvio(diferenca, redutora) {
    if (diferenca == null || Math.abs(diferenca) < 0.005) {
        return "";
    }
    const bom = redutora ? diferenca < 0 : diferenca > 0;
    return bom ? "favoravel" : "desfavoravel";
}

/* ---------- Animação de números (original da equipe) ---------- */

const reduzirMovimento = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

function animarNumero(elemento, final, formatador) {
    if (reduzirMovimento || !final) {
        elemento.textContent = formatador(final);
        return;
    }
    const inicio = performance.now();
    const duracao = 700;
    function passo(agora) {
        const t = Math.min((agora - inicio) / duracao, 1);
        elemento.textContent = formatador(final * (1 - Math.pow(1 - t, 3)));
        if (t < 1) {
            requestAnimationFrame(passo);
        } else {
            elemento.textContent = formatador(final);
        }
    }
    requestAnimationFrame(passo);
}

/* ---------- Preferências locais (só conveniência; nada de dados) ---------- */

const CHAVE_PREFS = "itaqui_ebitda_prefs";

const Prefs = (function () {
    let valores = { tema: "azul", modoEscuro: false, lembrarMenu: false, menuColapsado: false, cenario: "original", ano: null };
    try {
        const bruto = localStorage.getItem(CHAVE_PREFS);
        if (bruto) {
            valores = Object.assign(valores, JSON.parse(bruto));
        }
    } catch (e) {
        /* navegador sem armazenamento: segue com o padrão */
    }
    if (TEMAS.indexOf(valores.tema) === -1) {
        valores.tema = "azul";
    }
    return {
        get: function (chave) { return valores[chave]; },
        set: function (chave, valor) {
            valores[chave] = valor;
            try {
                localStorage.setItem(CHAVE_PREFS, JSON.stringify(valores));
            } catch (e) {
                /* ignora */
            }
        }
    };
})();

function aplicarTema(tema) {
    document.documentElement.setAttribute("data-theme", tema);
}

function aplicarModoEscuro(escuro) {
    const ativo = !!escuro;
    document.documentElement.setAttribute("data-mode", ativo ? "dark" : "light");
    const btn = $("btnModoEscuro");
    const icone = $("iconeModoEscuro");
    if (btn && icone) {
        btn.setAttribute("aria-pressed", String(ativo));
        btn.title = ativo ? "Ativar modo claro" : "Ativar modo escuro";
        icone.className = "fa-solid " + (ativo ? "fa-sun" : "fa-moon");
    }
    const sw = $("cfgModoEscuro");
    if (sw) {
        sw.checked = ativo;
    }
}

/* ---------- Avisos, confirmação, carregamento ---------- */

let temporizadorToast;

function mostrarAviso(mensagem, tipo) {
    const toast = $("toast");
    toast.textContent = mensagem;
    toast.classList.toggle("toast-erro", tipo === "erro");
    toast.classList.add("show");
    clearTimeout(temporizadorToast);
    temporizadorToast = setTimeout(function () {
        toast.classList.remove("show");
    }, tipo === "erro" ? 6000 : 3400);
}

function confirmar(titulo, texto, rotuloOk) {
    return new Promise(function (resolver) {
        const dlg = $("confirmDialog");
        $("confirmTitle").textContent = titulo;
        $("confirmText").textContent = texto;
        $("confirmOk").textContent = rotuloOk || "Confirmar";
        dlg.returnValue = "";
        dlg.addEventListener("close", function () {
            resolver(dlg.returnValue === "ok");
        }, { once: true });
        dlg.showModal();
    });
}

let pedidosEmAndamento = 0;
let temporizadorCarregando;

/* Mostra o indicador só se a operação passar de 250 ms (evita piscar) */
function carregando(ativo) {
    pedidosEmAndamento = Math.max(0, pedidosEmAndamento + (ativo ? 1 : -1));
    clearTimeout(temporizadorCarregando);
    if (pedidosEmAndamento > 0) {
        temporizadorCarregando = setTimeout(function () { $("carregando").hidden = false; }, 250);
    } else {
        $("carregando").hidden = true;
    }
}

function baixarArquivo(blob, nome) {
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = nome;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(function () { URL.revokeObjectURL(link.href); }, 1000);
}

/* Mensagem legível a partir de um erro da API */
function mensagemErro(erro) {
    if (!erro) {
        return "Erro desconhecido.";
    }
    let msg = erro.message || String(erro);
    if (erro.detalhes && erro.detalhes.length) {
        msg += " — " + erro.detalhes.slice(0, 3).join("; ");
    }
    return msg;
}

/* ---------- Menu lateral (original da equipe) ---------- */

const sidebar = $("sidebar");
const menuToggle = $("menuToggle");
const overlay = $("overlay");

function abrirMenuMobile(abrir) {
    sidebar.classList.toggle("open", abrir);
    overlay.classList.toggle("show", abrir);
    menuToggle.setAttribute("aria-expanded", String(abrir));
}

menuToggle.addEventListener("click", function () {
    if (window.innerWidth <= 850) {
        abrirMenuMobile(!sidebar.classList.contains("open"));
    } else {
        sidebar.classList.toggle("collapsed");
        if (Prefs.get("lembrarMenu")) {
            Prefs.set("menuColapsado", sidebar.classList.contains("collapsed"));
        }
    }
});

overlay.addEventListener("click", function () { abrirMenuMobile(false); });

window.addEventListener("resize", function () {
    if (window.innerWidth > 850) {
        abrirMenuMobile(false);
    }
});

/* Registro das telas: cada arquivo em js/telas/ adiciona a sua aqui */
const Telas = {};
