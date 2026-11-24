"use strict";

(function () {
  const API_BASE = (function () {
    const param = new URLSearchParams(location.search).get("api");
    if (param) return param.replace(/\/$/, "");
    if (location.protocol.indexOf("http") === 0 && location.port !== "5500" && location.port !== "5501") {
      return location.origin + "/api";
    }
    return "http://localhost:8080/api";
  })();

  const form = document.getElementById("form-login");
  const usuario = document.getElementById("usuario");
  const senha = document.getElementById("senha");
  const erro = document.getElementById("erro");
  const btnEntrar = document.getElementById("btn-entrar");
  const botaoOlho = document.getElementById("alternar-senha");
  const risco = document.getElementById("risco");
  const linkEsqueceu = document.getElementById("link-esqueceu");

  function mostrarMensagem(texto, info) {
    erro.textContent = texto;
    erro.classList.toggle("info", !!info);
    erro.classList.add("visivel");
  }

  function limparMensagem() {
    erro.textContent = "";
    erro.classList.remove("visivel", "info");
  }

  // Se já estiver autenticado, entra direto no sistema
  fetch(API_BASE + "/auth/me", { credentials: "include" })
    .then(function (r) { return r.ok ? r.json() : null; })
    .then(function (dados) {
      if (dados && dados.autenticado) {
        location.replace("index.html");
      }
    })
    .catch(function () { /* servidor offline; trata no submit */ });

  // Mostrar / ocultar senha
  botaoOlho.addEventListener("click", function () {
    const visivel = senha.type === "text";
    senha.type = visivel ? "password" : "text";
    risco.style.display = visivel ? "" : "none";
    botaoOlho.setAttribute("aria-pressed", String(!visivel));
    botaoOlho.setAttribute("aria-label", visivel ? "Mostrar senha" : "Ocultar senha");
  });

  // Aviso de recuperação de senha
  linkEsqueceu.addEventListener("click", function (ev) {
    ev.preventDefault();
    mostrarMensagem("Para redefinir sua senha, procure o administrador financeiro (GFin / TI EMAP).", true);
  });

  // Lembrar-me (guarda apenas o nome de usuário, nunca a senha)
  try {
    const salvo = localStorage.getItem("itaqui_usuario");
    if (salvo) {
      usuario.value = salvo;
      document.getElementById("lembrar").checked = true;
    }
  } catch (e) {}

  form.addEventListener("submit", async function (ev) {
    ev.preventDefault();
    limparMensagem();

    const userVal = usuario.value.trim();
    const passVal = senha.value;
    if (!userVal || !passVal) {
      mostrarMensagem("Informe o usuário e a senha para continuar.");
      (userVal ? senha : usuario).focus();
      return;
    }

    try {
      if (document.getElementById("lembrar").checked) {
        localStorage.setItem("itaqui_usuario", userVal);
      } else {
        localStorage.removeItem("itaqui_usuario");
      }
    } catch (e) {}

    btnEntrar.disabled = true;
    btnEntrar.textContent = "Entrando…";

    try {
      const resp = await fetch(API_BASE + "/auth/login", {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ usuario: userVal, senha: passVal })
      });

      let dados = null;
      try { dados = await resp.json(); } catch (e) {}

      if (!resp.ok) {
        const msg = (dados && dados.mensagem) ||
          (resp.status === 429 ? "Muitas tentativas. Aguarde alguns minutos e tente novamente."
            : "Usuário ou senha inválidos.");
        mostrarMensagem(msg);
        senha.value = "";
        senha.focus();
        return;
      }

      // Redireciona para o painel principal
      location.replace("index.html");
    } catch (e) {
      mostrarMensagem("Não foi possível conectar ao servidor. Verifique se o back-end está ativo.");
    } finally {
      btnEntrar.disabled = false;
      btnEntrar.textContent = "Entrar";
    }
  });
})();
