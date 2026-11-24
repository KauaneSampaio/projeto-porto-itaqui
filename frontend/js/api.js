/* =========================================
   API — única porta de comunicação com o back-end.
   Contrato completo: backend/README.md
========================================= */

"use strict";

/*
 * Endereço da API:
 *  - servido pelo próprio back-end (porta 8080): mesmo endereço (/api)
 *  - Live Server ou outra porta: http://localhost:8080/api
 *  - pode ser trocado com ?api=http://outro-endereco/api
 */
const API_BASE = (function () {
    const param = new URLSearchParams(location.search).get("api");
    if (param) {
        return param.replace(/\/$/, "");
    }
    if (location.protocol.indexOf("http") === 0 && location.port !== "5500" && location.port !== "5501") {
        return location.origin + "/api";
    }
    return "http://localhost:8080/api";
})();

class ErroApi extends Error {
    constructor(status, codigo, mensagem, detalhes, corpo) {
        super(mensagem);
        this.status = status;
        this.codigo = codigo;
        this.detalhes = detalhes || [];
        this.corpo = corpo;
    }
}

let CSRF_TOKEN = null;

function definirCsrf(token) {
    CSRF_TOKEN = token || null;
}

function marcarConexao(ok) {
    const chip = $("statusApi");
    chip.classList.toggle("ok", ok);
    chip.classList.toggle("erro", !ok);
    $("statusApiTexto").textContent = ok ? "Servidor conectado" : "Servidor offline";
}

/*
 * Faz a requisição e devolve o JSON (ou texto/blob).
 * opcoes.formato: "json" (padrão) | "texto" | "blob"
 * opcoes.aceitar: lista de status de erro que devolvem o corpo em vez de lançar (ex.: [422])
 */
async function chamarApi(metodo, caminho, corpo, opcoes) {
    const op = opcoes || {};
    const init = { method: metodo, headers: {}, credentials: "include" };
    if (CSRF_TOKEN && ["POST", "PUT", "PATCH", "DELETE"].indexOf(metodo.toUpperCase()) !== -1) {
        init.headers["X-CSRF-Token"] = CSRF_TOKEN;
    }
    if (corpo instanceof FormData) {
        init.body = corpo;
    } else if (corpo !== undefined && corpo !== null) {
        init.headers["Content-Type"] = "application/json";
        init.body = JSON.stringify(corpo);
    }

    if (!op.silencioso) {
        carregando(true);
    }
    let resposta;
    try {
        resposta = await fetch(API_BASE + caminho, init);
    } catch (e) {
        marcarConexao(false);
        throw new ErroApi(0, "SEM_CONEXAO", "Não foi possível falar com o servidor (" + API_BASE + ").");
    } finally {
        if (!op.silencioso) {
            carregando(false);
        }
    }
    marcarConexao(true);

    if (resposta.status === 401 && caminho.indexOf("/auth/") !== 0) {
        location.replace("login.html");
        throw new ErroApi(401, "NAO_AUTENTICADO", "Sessão expirada. Faça login novamente.");
    }

    if (resposta.status === 204) {
        return null;
    }

    const aceito = op.aceitar && op.aceitar.indexOf(resposta.status) !== -1;
    if (!resposta.ok && !aceito) {
        let dados = null;
        try {
            dados = await resposta.json();
        } catch (e) {
            /* corpo não é JSON */
        }
        throw new ErroApi(resposta.status, dados && dados.erro, (dados && dados.mensagem) ||
            "Erro " + resposta.status + " ao chamar " + caminho, dados && dados.detalhes, dados);
    }

    if (op.formato === "texto") {
        return (await resposta.text()).replace(/^﻿/, "");
    }
    if (op.formato === "blob") {
        return resposta.blob();
    }
    return resposta.json();
}

function q(params) {
    return "?" + new URLSearchParams(params).toString();
}

const enc = encodeURIComponent;

const Api = {
    /* Autenticação */
    authMe: function () { return chamarApi("GET", "/auth/me", null, { silencioso: true }); },
    authLogout: function () { return chamarApi("POST", "/auth/logout", null, { silencioso: true }); },
    alterarSenha: function (senhaAtual, novaSenha) {
        return chamarApi("POST", "/auth/alterar-senha", { senhaAtual: senhaAtual, novaSenha: novaSenha });
    },
    listarUsuarios: function () { return chamarApi("GET", "/auth/usuarios"); },
    criarUsuario: function (dados) { return chamarApi("POST", "/auth/usuarios", dados); },
    alterarStatusUsuario: function (login, ativo) {
        return chamarApi("PATCH", "/auth/usuarios/" + enc(login) + "/status", { ativo: !!ativo });
    },
    removerUsuario: function (login) { return chamarApi("DELETE", "/auth/usuarios/" + enc(login)); },

    meta: function () { return chamarApi("GET", "/meta", null, { silencioso: true }); },

    /* Estrutura da DRE (RF001) */
    estrutura: function () { return chamarApi("GET", "/dre/estrutura"); },
    criarConta: function (dados) { return chamarApi("POST", "/dre/contas", dados); },
    removerConta: function (codigo) { return chamarApi("DELETE", "/dre/contas/" + enc(codigo)); },

    /* Premissas e vínculos (RF003, RF004) */
    premissas: function () { return chamarApi("GET", "/premissas"); },
    atualizarPremissaOriginal: function (codigo, ajustes) {
        return chamarApi("PUT", "/premissas/" + enc(codigo), { ajustes: ajustes });
    },
    vinculos: function () { return chamarApi("GET", "/vinculos"); },
    criarVinculo: function (dados) { return chamarApi("POST", "/vinculos", dados); },
    removerVinculo: function (conta, premissa) {
        return chamarApi("DELETE", "/vinculos" + q({ conta: conta, premissa: premissa }));
    },

    /* Cenários (RF009) */
    cenarios: function () { return chamarApi("GET", "/cenarios", null, { silencioso: true }); },
    criarCenario: function (nome, copiarDe) {
        return chamarApi("POST", "/cenarios", { nome: nome, copiarDe: copiarDe || null });
    },
    renomearCenario: function (id, nome) { return chamarApi("PATCH", "/cenarios/" + enc(id), { nome: nome }); },
    removerCenario: function (id) { return chamarApi("DELETE", "/cenarios/" + enc(id)); },
    premissasCenario: function (id) { return chamarApi("GET", "/cenarios/" + enc(id) + "/premissas"); },
    ajustarPremissa: function (id, codigo, ajustes) {
        return chamarApi("PUT", "/cenarios/" + enc(id) + "/premissas/" + enc(codigo), { ajustes: ajustes });
    },
    removerAjustes: function (id, codigo) {
        return chamarApi("DELETE", "/cenarios/" + enc(id) + "/premissas/" + enc(codigo));
    },

    /* Cálculo (RF005–RF008) */
    dre: function (id, ano) { return chamarApi("GET", "/cenarios/" + enc(id) + "/dre" + q({ ano: ano })); },
    comparativo: function (id, ano, base) {
        return chamarApi("GET", "/cenarios/" + enc(id) + "/comparativo" + q({ ano: ano, base: base || "original" }));
    },

    /* Base e importação (RF002, CT-07) */
    importar: function (formData, simular) {
        return chamarApi("POST", "/importacao" + q({ simular: !!simular }), formData, { aceitar: [422] });
    },
    fechamento: function (dados) {
        return chamarApi("POST", "/base/fechamento", dados);
    },
    logs: function () {
        return chamarApi("GET", "/logs");
    },
    arquivoBase: function (nome) { return chamarApi("GET", "/base/arquivos/" + enc(nome), null, { formato: "texto" }); },
    restaurar: function () { return chamarApi("POST", "/base/restaurar"); },

    /* Evidências (RF011, CT-06) */
    evidenciaCsv: function (tipo, cenario, ano) {
        return chamarApi("GET", "/evidencias/" + tipo + ".csv" + q({ cenario: cenario, ano: ano }), null,
            { formato: "blob" });
    },
    desempenho: function (cenario, ano, repeticoes) {
        return chamarApi("GET", "/evidencias/desempenho" + q({ cenario: cenario, ano: ano, repeticoes: repeticoes || 20 }));
    }
};
