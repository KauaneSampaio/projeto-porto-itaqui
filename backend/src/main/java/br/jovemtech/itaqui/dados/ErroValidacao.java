package br.jovemtech.itaqui.dados;

/** Problema encontrado num arquivo. {@code linha} é a linha do arquivo (1 = cabeçalho). */
public record ErroValidacao(String arquivo, long linha, String campo, String mensagem) {
}
