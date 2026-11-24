package br.jovemtech.itaqui.dominio;

/**
 * Papel de uma conta na DRE. O sinal no cálculo vem daqui: os valores nos arquivos são
 * sempre positivos.
 */
public enum TipoConta {
    /** Só agrega as contas filhas; não recebe valores. */
    GRUPO,
    RECEITA_BRUTA,
    DEDUCAO_RECEITA,
    CUSTO,
    DESPESA_OPERACIONAL,
    /** Fica fora do EBITDA. */
    DEPRECIACAO_AMORTIZACAO;

    public boolean folha() {
        return this != GRUPO;
    }
}
