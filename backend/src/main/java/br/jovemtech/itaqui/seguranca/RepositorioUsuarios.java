package br.jovemtech.itaqui.seguranca;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.jovemtech.itaqui.config.PropriedadesApp;
import br.jovemtech.itaqui.dados.ArmazenamentoEstado;
import jakarta.annotation.PostConstruct;

/**
 * Repositório de usuários em JSON (disco local ou PostgreSQL via {@link ArmazenamentoEstado}),
 * com senhas armazenadas exclusivamente em hash PBKDF2.
 */
@Repository
public class RepositorioUsuarios {

    private static final String CHAVE = "usuarios.json";
    private static final Logger log = LoggerFactory.getLogger(RepositorioUsuarios.class);
    private static final TypeReference<List<Usuario>> TIPO_LISTA = new TypeReference<>() {
    };

    private final ArmazenamentoEstado armazenamento;
    private final ObjectMapper json;
    private final PropriedadesApp props;
    private final Map<String, Usuario> usuarios = new LinkedHashMap<>();

    public RepositorioUsuarios(ArmazenamentoEstado armazenamento, PropriedadesApp props, ObjectMapper json) {
        this.armazenamento = armazenamento;
        this.props = props;
        this.json = json;
    }

    @PostConstruct
    public synchronized void carregar() {
        usuarios.clear();
        Optional<String> salvo = armazenamento.ler(CHAVE);
        if (salvo.isPresent() && !salvo.get().isBlank()) {
            try {
                List<Usuario> lista = json.readValue(salvo.get(), TIPO_LISTA);
                for (Usuario u : lista) {
                    usuarios.put(normalizar(u.login()), u);
                }
                log.info("{} usuário(s) carregado(s) de {}", usuarios.size(), armazenamento.descricaoDestino(CHAVE));
                return;
            } catch (IOException e) {
                log.error("Falha ao ler {}; recriando usuários padrão", armazenamento.descricaoDestino(CHAVE), e);
            }
        }
        inicializarPadrao();
    }

    private void inicializarPadrao() {
        String senhaAdmin = props.auth().senhaAdmin();
        String senhaGestor = props.auth().senhaGestor();
        salvarInterno(new Usuario("admin", "Administrador GFin", "ADMIN", HashSenha.gerar(senhaAdmin), true));
        salvarInterno(new Usuario("gestor.emap", "Gestor Financeiro EMAP", "GESTOR", HashSenha.gerar(senhaGestor), true));
        gravar();
        log.info("Contas iniciais (admin, gestor.emap) gravadas em {}", armazenamento.descricaoDestino(CHAVE));
    }

    private void salvarInterno(Usuario u) {
        usuarios.put(normalizar(u.login()), u);
    }

    public synchronized Optional<Usuario> buscar(String login) {
        if (login == null || login.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(usuarios.get(normalizar(login)));
    }

    public synchronized boolean estaAtivo(String login) {
        if (login == null || login.isBlank()) {
            return false;
        }
        Usuario u = usuarios.get(normalizar(login));
        return u != null && u.ativo();
    }

    public synchronized List<Usuario> listar() {
        return List.copyOf(usuarios.values());
    }

    public synchronized Usuario criar(Usuario novo) {
        String chave = normalizar(novo.login());
        if (usuarios.containsKey(chave)) {
            throw new IllegalStateException("Já existe um usuário com o login: " + chave);
        }
        Usuario salvo = new Usuario(chave, novo.nome().trim(), novo.perfil(), novo.hashSenha(), novo.ativo());
        usuarios.put(chave, salvo);
        gravar();
        return salvo;
    }

    public synchronized Usuario alterarStatus(String login, boolean ativo) {
        String chave = normalizar(login);
        Usuario atual = usuarios.get(chave);
        if (atual == null) {
            throw new IllegalArgumentException("Usuário inexistente: " + login);
        }
        Usuario atualizado = new Usuario(atual.login(), atual.nome(), atual.perfil(), atual.hashSenha(), ativo);
        usuarios.put(chave, atualizado);
        gravar();
        return atualizado;
    }

    public synchronized Usuario remover(String login) {
        String chave = normalizar(login);
        Usuario removido = usuarios.remove(chave);
        if (removido == null) {
            throw new IllegalArgumentException("Usuário inexistente: " + login);
        }
        gravar();
        return removido;
    }

    public synchronized void atualizarSenha(String login, String novoHash) {
        String chave = normalizar(login);
        Usuario atual = usuarios.get(chave);
        if (atual == null) {
            throw new IllegalArgumentException("Usuário inexistente: " + login);
        }
        usuarios.put(chave, new Usuario(atual.login(), atual.nome(), atual.perfil(), novoHash, atual.ativo()));
        gravar();
    }

    private void gravar() {
        try {
            String conteudo = json.writerWithDefaultPrettyPrinter().writeValueAsString(new ArrayList<>(usuarios.values()));
            armazenamento.gravar(CHAVE, conteudo, true);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível serializar usuários", e);
        }
    }

    private static String normalizar(String login) {
        return login.trim().toLowerCase(Locale.ROOT);
    }
}
