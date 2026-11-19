package br.jovemtech.itaqui.dominio;

/** Linha da estrutura da DRE. {@code codigoPai} é nulo para contas raiz. */
public record Conta(String codigo, String nome, String codigoPai, TipoConta tipo, boolean opex, int ordem) {

    public boolean folha() {
        return tipo.folha();
    }
}
