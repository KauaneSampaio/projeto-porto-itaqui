package br.jovemtech.itaqui.seguranca;

/** Usuário persistido do sistema (nunca expor {@code hashSenha} em respostas JSON). */
public record Usuario(String login, String nome, String perfil, String hashSenha, boolean ativo) {
}
