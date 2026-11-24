package br.jovemtech.itaqui.seguranca;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.jovemtech.itaqui.api.ApiException;
import br.jovemtech.itaqui.dados.RepositorioLogs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Endpoints de autenticação segura por sessão + CSRF + rate limit. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(
            @NotBlank(message = "Informe o usuário") @Size(max = 60) String usuario,
            @NotBlank(message = "Informe a senha") @Size(max = 128) String senha) {
    }

    public record AlterarSenhaRequest(
            @NotBlank(message = "Informe a senha atual") String senhaAtual,
            @NotBlank(message = "Informe a nova senha") @Size(min = 8, max = 128, message = "A nova senha deve ter no mínimo 8 caracteres") String novaSenha) {
    }

    public record SessaoResposta(boolean autenticado, String usuario, String nome, String perfil, String csrfToken) {
        static SessaoResposta anonimo() {
            return new SessaoResposta(false, null, null, null, null);
        }
    }

    public record UsuarioResposta(String login, String nome, String perfil, boolean ativo) {
        static UsuarioResposta de(Usuario u) {
            return new UsuarioResposta(u.login(), u.nome(), u.perfil(), u.ativo());
        }
    }

    public record NovoUsuarioRequest(
            @NotBlank(message = "Informe o login")
            @Pattern(regexp = "^[a-zA-Z0-9._-]{3,40}$", message = "Login deve ter 3 a 40 caracteres (letras, números, ponto, hífen ou _)")
            String login,
            @NotBlank(message = "Informe o nome completo")
            @Size(min = 2, max = 80, message = "Nome deve ter entre 2 e 80 caracteres")
            String nome,
            @NotBlank(message = "Informe o perfil")
            @Pattern(regexp = "^(ADMIN|GESTOR)$", message = "Perfil deve ser ADMIN ou GESTOR")
            String perfil,
            @NotBlank(message = "Informe a senha inicial")
            @Size(min = 8, max = 128, message = "A senha deve ter no mínimo 8 caracteres")
            String senha) {
    }

    public record StatusUsuarioRequest(@NotNull Boolean ativo) {
    }

    private final RepositorioUsuarios usuarios;
    private final LimitadorTentativas limitador;
    private final RepositorioLogs logs;

    public AuthController(RepositorioUsuarios usuarios, LimitadorTentativas limitador, RepositorioLogs logs) {
        this.usuarios = usuarios;
        this.limitador = limitador;
        this.logs = logs;
    }

    @GetMapping("/me")
    public SessaoResposta me(HttpServletRequest request) {
        HttpSession sessao = request.getSession(false);
        if (sessao == null) {
            return SessaoResposta.anonimo();
        }
        String login = (String) sessao.getAttribute(FiltroAutenticacao.ATTR_USUARIO);
        if (login == null) {
            return SessaoResposta.anonimo();
        }
        return new SessaoResposta(
                true,
                login,
                (String) sessao.getAttribute(FiltroAutenticacao.ATTR_NOME),
                (String) sessao.getAttribute(FiltroAutenticacao.ATTR_PERFIL),
                (String) sessao.getAttribute(FiltroAutenticacao.ATTR_CSRF));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        String loginLimpo = req.usuario().trim().toLowerCase(Locale.ROOT);
        String ip = request.getRemoteAddr();

        if (limitador.estaBloqueado(ip, loginLimpo)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(
                            "erro", "MUITAS_TENTATIVAS",
                            "mensagem", "Muitas tentativas inválidas. Aguarde 15 minutos e tente novamente."));
        }

        Optional<Usuario> opt = usuarios.buscar(loginLimpo);
        boolean senhaOk;
        if (opt.isPresent() && opt.get().ativo()) {
            senhaOk = HashSenha.verificar(req.senha(), opt.get().hashSenha());
        } else {
            HashSenha.verificarFicticio(req.senha());
            senhaOk = false;
        }

        if (!senhaOk) {
            boolean bloqueou = limitador.registrarFalha(ip, loginLimpo);
            logs.salvar(loginLimpo, bloqueou ? "LOGIN_BLOQUEADO" : "LOGIN_FALHA", "AUTH", loginLimpo,
                    "Tentativa de login inválida (IP " + ip + ")");
            if (bloqueou) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of(
                                "erro", "MUITAS_TENTATIVAS",
                                "mensagem", "Conta temporariamente bloqueada por 15 minutos após excesso de tentativas."));
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("erro", "CREDENCIAIS_INVALIDAS", "mensagem", "Usuário ou senha inválidos."));
        }

        limitador.limpar(ip, loginLimpo);

        // Proteção contra session fixation: invalida qualquer sessão anterior antes de criar a nova
        HttpSession antiga = request.getSession(false);
        if (antiga != null) {
            antiga.invalidate();
        }
        HttpSession nova = request.getSession(true);
        Usuario u = opt.get();
        String csrf = HashSenha.gerarTokenSeguro();
        nova.setAttribute(FiltroAutenticacao.ATTR_USUARIO, u.login());
        nova.setAttribute(FiltroAutenticacao.ATTR_NOME, u.nome());
        nova.setAttribute(FiltroAutenticacao.ATTR_PERFIL, u.perfil());
        nova.setAttribute(FiltroAutenticacao.ATTR_CSRF, csrf);

        logs.salvar(u.login(), "LOGIN", "AUTH", u.login(), "Acesso autorizado ao sistema (IP " + ip + ")");

        return ResponseEntity.ok(new SessaoResposta(true, u.login(), u.nome(), u.perfil(), csrf));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession sessao = request.getSession(false);
        if (sessao != null) {
            String login = (String) sessao.getAttribute(FiltroAutenticacao.ATTR_USUARIO);
            if (login != null) {
                logs.salvar(login, "LOGOUT", "AUTH", login, "Sessão encerrada pelo usuário");
            }
            sessao.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/alterar-senha")
    public ResponseEntity<?> alterarSenha(@Valid @RequestBody AlterarSenhaRequest req, HttpServletRequest request) {
        HttpSession sessao = request.getSession(false);
        String login = sessao != null ? (String) sessao.getAttribute(FiltroAutenticacao.ATTR_USUARIO) : null;
        if (login == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("erro", "NAO_AUTENTICADO", "mensagem", "Faça login para alterar a senha."));
        }
        Usuario u = usuarios.buscar(login).orElse(null);
        if (u == null || !HashSenha.verificar(req.senhaAtual(), u.hashSenha())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("erro", "SENHA_ATUAL_INCORRETA", "mensagem", "A senha atual informada está incorreta."));
        }
        String nova = req.novaSenha();
        boolean temLetra = nova.chars().anyMatch(Character::isLetter);
        boolean temDigito = nova.chars().anyMatch(Character::isDigit);
        if (!temLetra || !temDigito) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("erro", "SENHA_FRACA", "mensagem", "A nova senha deve conter letras e números (mínimo 8 caracteres)."));
        }
        usuarios.atualizarSenha(login, HashSenha.gerar(nova));
        request.changeSessionId();
        logs.salvar(login, "ALTERAR_SENHA", "AUTH", login, "Senha alterada pelo próprio usuário");
        return ResponseEntity.ok(Map.of("mensagem", "Senha alterada com sucesso."));
    }

    // ------------------------------------------------------------------ gestão de contas (somente ADMIN)

    @GetMapping("/usuarios")
    public List<UsuarioResposta> listarUsuarios(HttpServletRequest request) {
        exigirAdmin(request);
        return usuarios.listar().stream().map(UsuarioResposta::de).toList();
    }

    @PostMapping("/usuarios")
    public ResponseEntity<UsuarioResposta> criarUsuario(@Valid @RequestBody NovoUsuarioRequest req,
            HttpServletRequest request) {
        String adminLogin = exigirAdmin(request);
        String senha = req.senha();
        boolean temLetra = senha.chars().anyMatch(Character::isLetter);
        boolean temDigito = senha.chars().anyMatch(Character::isDigit);
        if (!temLetra || !temDigito) {
            throw ApiException.regra("SENHA_FRACA", "A senha inicial deve conter letras e números (mínimo 8 caracteres).");
        }
        String loginLimpo = req.login().trim().toLowerCase(Locale.ROOT);
        if (usuarios.buscar(loginLimpo).isPresent()) {
            throw ApiException.conflito("USUARIO_DUPLICADO", "Já existe uma conta com o login '" + loginLimpo + "'.");
        }
        Usuario criado = usuarios.criar(new Usuario(loginLimpo, req.nome().trim(), req.perfil(), HashSenha.gerar(senha), true));
        logs.salvar(adminLogin, "CRIAR_USUARIO", "USUARIO", criado.login(),
                "Conta criada: " + criado.login() + " (" + criado.nome() + ", perfil " + criado.perfil() + ")");
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResposta.de(criado));
    }

    @PatchMapping("/usuarios/{login}/status")
    public UsuarioResposta alterarStatusUsuario(@PathVariable String login,
            @Valid @RequestBody StatusUsuarioRequest req, HttpServletRequest request) {
        String adminLogin = exigirAdmin(request);
        String alvo = login.trim().toLowerCase(Locale.ROOT);
        if (alvo.equals(adminLogin) && !req.ativo()) {
            throw ApiException.conflito("AUTO_BLOQUEIO", "Você não pode desativar a própria conta logada.");
        }
        if (usuarios.buscar(alvo).isEmpty()) {
            throw ApiException.naoEncontrado("Usuário inexistente: " + alvo);
        }
        Usuario atualizado = usuarios.alterarStatus(alvo, req.ativo());
        logs.salvar(adminLogin, "ATUALIZAR_USUARIO", "USUARIO", atualizado.login(),
                "Conta " + atualizado.login() + (atualizado.ativo() ? " reativada" : " desativada"));
        return UsuarioResposta.de(atualizado);
    }

    @DeleteMapping("/usuarios/{login}")
    public ResponseEntity<Void> removerUsuario(@PathVariable String login, HttpServletRequest request) {
        String adminLogin = exigirAdmin(request);
        String alvo = login.trim().toLowerCase(Locale.ROOT);
        if (alvo.equals(adminLogin)) {
            throw ApiException.conflito("AUTO_REMOCAO", "Você não pode excluir a própria conta logada.");
        }
        if (usuarios.buscar(alvo).isEmpty()) {
            throw ApiException.naoEncontrado("Usuário inexistente: " + alvo);
        }
        Usuario removido = usuarios.remover(alvo);
        logs.salvar(adminLogin, "REMOVER_USUARIO", "USUARIO", removido.login(),
                "Conta removida: " + removido.login() + " (" + removido.nome() + ")");
        return ResponseEntity.noContent().build();
    }

    private static String exigirAdmin(HttpServletRequest request) {
        HttpSession sessao = request.getSession(false);
        String login = sessao != null ? (String) sessao.getAttribute(FiltroAutenticacao.ATTR_USUARIO) : null;
        String perfil = sessao != null ? (String) sessao.getAttribute(FiltroAutenticacao.ATTR_PERFIL) : null;
        if (login == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "NAO_AUTENTICADO", "Faça login para continuar.", List.of());
        }
        if (!"ADMIN".equals(perfil)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACESSO_NEGADO",
                    "Apenas administradores podem gerenciar contas de usuário.", List.of());
        }
        return login;
    }
}
