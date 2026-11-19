package br.jovemtech.itaqui.calculo;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.jovemtech.itaqui.calculo.ResultadoDre.Indicadores;
import br.jovemtech.itaqui.calculo.ResultadoDre.Perspectivas;
import br.jovemtech.itaqui.dados.ArquivosSemente;
import br.jovemtech.itaqui.dados.CarregadorBase;
import br.jovemtech.itaqui.dominio.Base;
import br.jovemtech.itaqui.dominio.Cenario;
import br.jovemtech.itaqui.dominio.Periodo;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confronto automático com a Planilha Oráculo (CT-01/02/03). Só roda quando existir
 * {@code src/test/resources/oraculo/esperado.csv}. Veja o README dessa pasta.
 */
class OraculoTest {

    private static final Map<String, Function<Indicadores, Perspectivas>> INDICADORES = Map.of(
            "receita_bruta", Indicadores::receitaBruta,
            "deducoes", Indicadores::deducoes,
            "receita_liquida", Indicadores::receitaLiquida,
            "custos", Indicadores::custos,
            "despesas_operacionais", Indicadores::despesasOperacionais,
            "ebitda", Indicadores::ebitda,
            "margem_ebitda_pct", Indicadores::margemEbitdaPct,
            "depreciacao_amortizacao", Indicadores::depreciacaoAmortizacao);

    private static String recurso(String caminho) throws IOException {
        try (InputStream in = OraculoTest.class.getClassLoader().getResourceAsStream(caminho)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("﻿", "");
        }
    }

    private static BigDecimal numero(String t) {
        String s = t.trim();
        if (s.contains(",")) {
            s = s.replace(".", "").replace(',', '.');
        }
        return new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
    }

    @Test
    @DisplayName("Motor confere com o esperado.csv da Planilha Oráculo")
    void confereComOraculo() throws IOException {
        String esperado = recurso("oraculo/esperado.csv");
        Assumptions.assumeTrue(esperado != null, "oraculo/esperado.csv ainda não foi entregue");

        Base base = new CarregadorBase().carregar(ArquivosSemente.ler(), "ORACULO").base();
        Map<String, Cenario> cenarios = new HashMap<>();
        cenarios.put(Cenario.ID_ORIGINAL, Cenario.original());
        String ajustes = recurso("oraculo/ajustes.csv");
        if (ajustes != null) {
            for (String linha : ajustes.lines().skip(1).filter(l -> !l.isBlank()).toList()) {
                String[] c = linha.split(";");
                Cenario atual = cenarios.computeIfAbsent(c[0].trim(),
                        id -> new Cenario(id, id, false, Instant.now(), Instant.now(), Map.of()));
                Map<Periodo, BigDecimal> m = new TreeMap<>();
                m.put(Periodo.parse(c[2]), numero(c[3]));
                cenarios.put(atual.id(), atual.comAjustes(c[1].trim(), m));
            }
        }

        MotorCalculo motor = new MotorCalculo(new ProjecaoPorIndice());
        Map<String, ResultadoDre> cache = new HashMap<>();
        List<String> divergencias = new ArrayList<>();
        int conferidos = 0;
        for (String linha : esperado.lines().skip(1).filter(l -> !l.isBlank()).toList()) {
            String[] c = linha.split(";");
            String cenario = c[0].trim();
            int ano = Integer.parseInt(c[1].trim());
            String mes = c[2].trim();
            String indicador = c[3].trim();
            String perspectiva = c[4].trim();
            BigDecimal valorEsperado = numero(c[5]);

            ResultadoDre r = cache.computeIfAbsent(cenario + ano,
                    k -> motor.calcular(base, cenarios.get(cenario), ano));
            Indicadores ind = mes.equalsIgnoreCase("TOTAL") ? r.indicadores()
                    : r.indicadoresMensais().get(Integer.parseInt(mes) - 1).indicadores();
            Perspectivas p = INDICADORES.get(indicador).apply(ind);
            BigDecimal obtido = switch (perspectiva) {
                case "orcado" -> p.orcado();
                case "realizado" -> p.realizado();
                case "forecast" -> p.forecast();
                default -> throw new IllegalArgumentException("Perspectiva desconhecida: " + perspectiva);
            };
            conferidos++;
            if (obtido == null || obtido.compareTo(valorEsperado) != 0) {
                divergencias.add(linha + " → obtido " + obtido);
            }
        }
        assertThat(divergencias).as("%d valores conferidos", conferidos).isEmpty();
    }
}
