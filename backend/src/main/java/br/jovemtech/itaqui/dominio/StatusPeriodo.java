package br.jovemtech.itaqui.dominio;

public enum StatusPeriodo {
    /** Mês até a data de corte: usa o realizado, que nunca é alterado. */
    FECHADO,
    /** Mês depois da data de corte: usa a projeção do cenário. */
    ABERTO
}
