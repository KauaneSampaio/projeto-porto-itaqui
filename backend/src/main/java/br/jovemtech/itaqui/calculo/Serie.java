package br.jovemtech.itaqui.calculo;

import java.math.BigDecimal;

/** Valores de uma conta nos 12 meses de um ano, em precisão cheia (uso interno do motor). */
final class Serie {

    final BigDecimal[] orcado = new BigDecimal[12];
    final BigDecimal[] realizado = new BigDecimal[12];
    final BigDecimal[] projetado = new BigDecimal[12];
    final BigDecimal[] forecast = new BigDecimal[12];

    void somar(Serie outra) {
        for (int i = 0; i < 12; i++) {
            orcado[i] = Valores.soma(orcado[i], outra.orcado[i]);
            realizado[i] = Valores.soma(realizado[i], outra.realizado[i]);
            projetado[i] = Valores.soma(projetado[i], outra.projetado[i]);
            forecast[i] = Valores.soma(forecast[i], outra.forecast[i]);
        }
    }
}
