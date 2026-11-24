package br.jovemtech.itaqui.seguranca;

/** Propaga o login do usuário autenticado na thread da requisição HTTP atual. */
public final class ContextoUsuario {

    private static final ThreadLocal<String> ATUAL = new ThreadLocal<>();

    private ContextoUsuario() {
    }

    public static void definir(String login) {
        ATUAL.set(login);
    }

    public static String atual() {
        String u = ATUAL.get();
        return (u == null || u.isBlank()) ? "sistema" : u;
    }

    public static void limpar() {
        ATUAL.remove();
    }
}
