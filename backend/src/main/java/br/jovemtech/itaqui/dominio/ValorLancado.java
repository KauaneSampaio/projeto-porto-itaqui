package br.jovemtech.itaqui.dominio;

import java.math.BigDecimal;

/** Valor mensal de uma conta folha (sempre positivo). */
public record ValorLancado(String codigoConta, Periodo periodo, TipoValor tipo, BigDecimal valor) {
}
