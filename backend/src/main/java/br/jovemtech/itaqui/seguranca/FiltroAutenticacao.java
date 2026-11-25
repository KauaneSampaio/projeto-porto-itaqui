package br.jovemtech.itaqui.seguranca;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import br.jovemtech.itaqui.config.PropriedadesApp;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Barreira de segurança no servidor:
 * <ul>
 *   <li>Aplica cabeçalhos HTTP de segurança (CSP, X-Frame-Options, nosniff, HSTS sob HTTPS).</li>
 *   <li>Bloqueia qualquer rota da API sem sessão autenticada e usuário ativo (HTTP 401).</li>
 *   <li>Redireciona páginas internas sem sessão autenticada para {@code /login.html} (HTTP 302).</li>
 *   <li>Revoga imediatamente sessões de contas desativadas ou removidas pelo administrador.</li>
 *   <li>Valida token CSRF ({@code X-CSRF-Token}) em mutações autenticadas da API (HTTP 403).</li>
 *   <li>Restringe rotas administrativas ({@code /api/evidencias/**}) ao perfil {@code ADMIN}.</li>
 *   <li>Propaga o usuário autenticado em {@link ContextoUsuario} para os logs de auditoria.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltroAutenticacao extends OncePerRequestFilter {

    public static final String ATTR_USUARIO = "ITAQUI_AUTH_USUARIO";
    public static final String ATTR_NOME = "ITAQUI_AUTH_NOME";
    public static final String ATTR_PERFIL = "ITAQUI_AUTH_PERFIL";
    public static final String ATTR_CSRF = "ITAQUI_AUTH_CSRF";
    public static final String HEADER_CSRF = "X-CSRF-Token";

    private static final String CSP = "default-src 'self'; "
            + "script-src 'self' https://cdn.jsdelivr.net; "
            + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdnjs.cloudflare.com; "
            + "font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com data:; "
            + "img-src 'self' data:; "
            + "connect-src 'self'; "
            + "frame-ancestors 'none'; "
            + "base-uri 'self'; "
            + "form-action 'self'";

    private static final Set<String> ROTAS_PUBLICAS = Set.of(
            "/login.html",
            "/login",
            "/css/login.css",
            "/js/login.js",
            "/img/fundo.jpg",
            "/img/logo.png",
            "/favicon.ico",
            "/api/auth/login",
            "/api/auth/me",
            "/api/auth/logout"
    );

    private static final Set<String> METODOS_MUTAVEIS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final PropriedadesApp props;
    private final RepositorioUsuarios usuarios;

    public FiltroAutenticacao(PropriedadesApp props, RepositorioUsuarios usuarios) {
        this.props = props;
        this.usuarios = usuarios;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = normalizarPath(request.getRequestURI());
        String metodo = request.getMethod();

        aplicarCabecalhosSeguranca(request, response, path);

        // Preflight CORS
        if ("OPTIONS".equalsIgnoreCase(metodo)) {
            chain.doFilter(request, response);
            return;
        }

        // Atalho amigável /login -> /login.html
        if ("/login".equals(path)) {
            response.sendRedirect("/login.html");
            return;
        }

        if (!props.auth().habilitada()) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession sessao = request.getSession(false);
        String usuario = sessao != null ? (String) sessao.getAttribute(ATTR_USUARIO) : null;

        // Se a conta foi desativada ou removida enquanto logada, revoga a sessão imediatamente
        if (usuario != null && !usuarios.estaAtivo(usuario)) {
            sessao.invalidate();
            sessao = null;
            usuario = null;
        }

        if (ROTAS_PUBLICAS.contains(path)) {
            if (usuario != null) {
                ContextoUsuario.definir(usuario);
            }
            try {
                chain.doFilter(request, response);
            } finally {
                ContextoUsuario.limpar();
            }
            return;
        }

        // Bloqueia qualquer rota não pública sem sessão válida
        if (usuario == null) {
            if (path.startsWith("/api/")) {
                responderErroJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "NAO_AUTENTICADO", "Faça login para acessar o sistema.");
            } else {
                response.sendRedirect("/login.html");
            }
            return;
        }

        // Rotas administrativas da API (somente ADMIN; contas.csv também é usado pelo botão Exportar CSV da DRE)
        if (path.startsWith("/api/evidencias/") && !"/api/evidencias/contas.csv".equals(path)) {
            String perfil = (String) sessao.getAttribute(ATTR_PERFIL);
            if (!"ADMIN".equals(perfil)) {
                responderErroJson(response, HttpServletResponse.SC_FORBIDDEN,
                        "ACESSO_NEGADO", "Apenas administradores podem acessar as evidências do sistema.");
                return;
            }
        }

        // Proteção CSRF em requisições mutáveis da API
        if (path.startsWith("/api/") && METODOS_MUTAVEIS.contains(metodo.toUpperCase())) {
            String tokenSessao = (String) sessao.getAttribute(ATTR_CSRF);
            String tokenRecebido = request.getHeader(HEADER_CSRF);
            if (!tokensIguais(tokenSessao, tokenRecebido)) {
                responderErroJson(response, HttpServletResponse.SC_FORBIDDEN,
                        "CSRF_INVALIDO", "Token de segurança inválido ou ausente. Recarregue a página.");
                return;
            }
        }

        ContextoUsuario.definir(usuario);
        try {
            chain.doFilter(request, response);
        } finally {
            ContextoUsuario.limpar();
        }
    }

    private static String normalizarPath(String uri) {
        if (uri == null || uri.isEmpty()) {
            return "/";
        }
        int pontoVirgula = uri.indexOf(';');
        String limpo = pontoVirgula >= 0 ? uri.substring(0, pontoVirgula) : uri;
        while (limpo.contains("//")) {
            limpo = limpo.replace("//", "/");
        }
        return limpo;
    }

    private static void aplicarCabecalhosSeguranca(HttpServletRequest request, HttpServletResponse response, String path) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
        response.setHeader("Content-Security-Policy", CSP);
        if (request.isSecure()) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
        if (path.startsWith("/api/")) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            response.setHeader("Pragma", "no-cache");
        }
    }

    private static boolean tokensIguais(String esperado, String recebido) {
        if (esperado == null || recebido == null) {
            return false;
        }
        return MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8),
                recebido.getBytes(StandardCharsets.UTF_8));
    }

    private static void responderErroJson(HttpServletResponse response, int status, String erro, String mensagem)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"erro\":\"" + erro + "\",\"mensagem\":\"" + mensagem + "\"}");
    }
}
