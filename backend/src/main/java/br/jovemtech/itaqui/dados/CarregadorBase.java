package br.jovemtech.itaqui.dados;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import br.jovemtech.itaqui.dominio.Base;
import br.jovemtech.itaqui.dominio.Conta;
import br.jovemtech.itaqui.dominio.Periodo;
import br.jovemtech.itaqui.dominio.Premissa;
import br.jovemtech.itaqui.dominio.TipoConta;
import br.jovemtech.itaqui.dominio.TipoValor;
import br.jovemtech.itaqui.dominio.ValorLancado;
import br.jovemtech.itaqui.dominio.Vinculo;

import static br.jovemtech.itaqui.dados.ArquivosBase.CONFIG;
import static br.jovemtech.itaqui.dados.ArquivosBase.CONTAS;
import static br.jovemtech.itaqui.dados.ArquivosBase.PREMISSAS;
import static br.jovemtech.itaqui.dados.ArquivosBase.VALORES;
import static br.jovemtech.itaqui.dados.ArquivosBase.VINCULOS;

/**
 * Lê e valida os 5 CSVs de uma base. Regra "tudo ou nada": se houver qualquer erro, não
 * devolve base (RF002, RNF001, CT-07). Avisos não impedem a carga.
 *
 * <p>Formato: separador {@code ;}, UTF-8, cabeçalho obrigatório, decimal com vírgula ou
 * ponto (sem separador de milhar), valores monetários sempre positivos.
 */
public final class CarregadorBase {

    public record Resultado(Base base, RelatorioImportacao relatorio) {
    }

    private record Linha(long numero, Map<String, String> campos) {
        String get(String coluna) {
            String v = campos.get(coluna);
            return v == null ? "" : v.trim();
        }
    }

    private static final int MAX_ERROS = 500;

    private static final CSVFormat FORMATO = CSVFormat.DEFAULT.builder()
            .setDelimiter(';')
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .setIgnoreHeaderCase(true)
            .build();

    /** Estado de uma execução de validação. */
    private static final class Contexto {
        final List<ErroValidacao> erros = new ArrayList<>();
        final List<ErroValidacao> avisos = new ArrayList<>();
        int omitidos;

        void erro(String arquivo, long linha, String campo, String msg) {
            if (erros.size() < MAX_ERROS) {
                erros.add(new ErroValidacao(arquivo, linha, campo, msg));
            } else {
                omitidos++;
            }
        }

        void aviso(String arquivo, long linha, String campo, String msg) {
            if (avisos.size() < MAX_ERROS) {
                avisos.add(new ErroValidacao(arquivo, linha, campo, msg));
            }
        }
    }

    public Resultado carregar(Map<String, String> arquivos, String fonte) {
        Contexto ctx = new Contexto();
        for (String nome : ArquivosBase.TODOS) {
            if (!arquivos.containsKey(nome) || arquivos.get(nome) == null || arquivos.get(nome).isBlank()) {
                ctx.erro(nome, 0, null, "Arquivo obrigatório ausente ou vazio");
            }
        }
        if (!ctx.erros.isEmpty()) {
            return invalido(ctx, 0, 0, 0, 0, null);
        }

        Map<String, String> config = lerConfig(arquivos.get(CONFIG), ctx);
        Periodo corte = null;
        String textoCorte = config.get("data_corte");
        if (textoCorte == null || textoCorte.isBlank()) {
            ctx.erro(CONFIG, 0, "data_corte", "Chave 'data_corte' obrigatória (AAAA-MM do último mês fechado)");
        } else {
            try {
                corte = Periodo.parse(textoCorte);
            } catch (IllegalArgumentException e) {
                ctx.erro(CONFIG, 0, "data_corte", e.getMessage());
            }
        }
        String origem = config.getOrDefault("origem", Base.ORIGEM_FICTICIO).toUpperCase(Locale.ROOT);
        String unidade = config.getOrDefault("unidade", "R$");
        int versao = 1;
        if (config.containsKey("versao")) {
            try {
                versao = Integer.parseInt(config.get("versao"));
            } catch (NumberFormatException e) {
                ctx.erro(CONFIG, 0, "versao", "Versão deve ser um número inteiro");
            }
        }

        Map<String, Conta> contas = new LinkedHashMap<>();
        Map<String, Long> linhaConta = new HashMap<>();
        lerContas(arquivos.get(CONTAS), ctx, contas, linhaConta);

        List<ValorLancado> valores = lerValores(arquivos.get(VALORES), ctx, contas, corte);
        Map<String, Premissa> premissas = lerPremissas(arquivos.get(PREMISSAS), ctx);
        List<Vinculo> vinculos = lerVinculos(arquivos.get(VINCULOS), ctx, contas, premissas);

        if (corte != null) {
            avisarRealizadosFaltando(ctx, contas, valores, corte);
        }

        if (!ctx.erros.isEmpty()) {
            return invalido(ctx, contas.size(), valores.size(), premissas.size(), vinculos.size(), corte);
        }
        Base base = new Base(List.copyOf(contas.values()), valores, List.copyOf(premissas.values()), vinculos,
                corte, origem, unidade, versao, fonte, Instant.now());
        RelatorioImportacao rel = new RelatorioImportacao(true, false, List.copyOf(ctx.erros),
                List.copyOf(ctx.avisos),
                new RelatorioImportacao.Resumo(contas.size(), valores.size(), premissas.size(), vinculos.size(),
                        corte));
        return new Resultado(base, rel);
    }

    private Resultado invalido(Contexto ctx, int nContas, int nValores, int nPremissas, int nVinculos, Periodo corte) {
        if (ctx.omitidos > 0) {
            ctx.erros.add(new ErroValidacao("*", 0, null, ctx.omitidos + " erro(s) adicionais omitidos"));
        }
        RelatorioImportacao rel = new RelatorioImportacao(false, false, List.copyOf(ctx.erros),
                List.copyOf(ctx.avisos),
                new RelatorioImportacao.Resumo(nContas, nValores, nPremissas, nVinculos, corte));
        return new Resultado(null, rel);
    }

    // ------------------------------------------------------------------ leitura genérica

    private List<Linha> ler(String arquivo, String conteudo, List<String> colunas, Contexto ctx) {
        String texto = conteudo.startsWith("﻿") ? conteudo.substring(1) : conteudo;
        List<Linha> linhas = new ArrayList<>();
        try (CSVParser parser = FORMATO.parse(new StringReader(texto))) {
            Map<String, Integer> cabecalho = new HashMap<>();
            parser.getHeaderMap().forEach((k, v) -> cabecalho.put(k.trim().toLowerCase(Locale.ROOT), v));
            List<String> faltando = colunas.stream().filter(c -> !cabecalho.containsKey(c)).toList();
            if (!faltando.isEmpty()) {
                ctx.erro(arquivo, 1, String.join(",", faltando),
                        "Coluna(s) obrigatória(s) ausente(s) no cabeçalho. Esperado: " + String.join(";", colunas));
                return linhas;
            }
            for (CSVRecord r : parser) {
                long numero = r.getRecordNumber() + 1;
                if (r.size() != cabecalho.size()) {
                    ctx.erro(arquivo, numero, null, "Quantidade de colunas diferente do cabeçalho (" + r.size()
                            + " em vez de " + cabecalho.size() + ")");
                    continue;
                }
                Map<String, String> campos = new HashMap<>();
                cabecalho.forEach((nome, idx) -> campos.put(nome, r.get(idx)));
                linhas.add(new Linha(numero, campos));
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException | UncheckedIOException e) {
            ctx.erro(arquivo, 0, null, "Arquivo CSV mal formado: " + e.getMessage());
        }
        return linhas;
    }

    static BigDecimal numero(String texto) {
        String s = texto.trim().replace(" ", "");
        if (s.isEmpty()) {
            throw new NumberFormatException("vazio");
        }
        if (s.contains(",")) {
            s = s.replace(".", "").replace(',', '.');
        }
        return new BigDecimal(s);
    }

    static Boolean booleano(String texto) {
        return switch (texto.trim().toUpperCase(Locale.ROOT)) {
            case "S", "SIM", "TRUE", "1", "Y", "YES" -> Boolean.TRUE;
            case "N", "NAO", "NÃO", "FALSE", "0", "NO", "" -> Boolean.FALSE;
            default -> null;
        };
    }

    // ------------------------------------------------------------------ config

    private Map<String, String> lerConfig(String conteudo, Contexto ctx) {
        Map<String, String> config = new HashMap<>();
        for (Linha l : ler(CONFIG, conteudo, List.of("chave", "valor"), ctx)) {
            String chave = l.get("chave").toLowerCase(Locale.ROOT);
            if (chave.isEmpty()) {
                ctx.erro(CONFIG, l.numero(), "chave", "Chave vazia");
            } else if (config.put(chave, l.get("valor")) != null) {
                ctx.erro(CONFIG, l.numero(), "chave", "Chave duplicada: " + chave);
            }
        }
        return config;
    }

    // ------------------------------------------------------------------ contas

    private void lerContas(String conteudo, Contexto ctx, Map<String, Conta> contas, Map<String, Long> linhaConta) {
        List<Linha> linhas = ler(CONTAS, conteudo,
                List.of("codigo", "nome", "codigo_pai", "tipo", "opex", "ordem"), ctx);
        for (Linha l : linhas) {
            String codigo = l.get("codigo");
            String nome = l.get("nome");
            boolean ok = true;
            if (codigo.isEmpty()) {
                ctx.erro(CONTAS, l.numero(), "codigo", "Código vazio");
                ok = false;
            } else if (contas.containsKey(codigo)) {
                ctx.erro(CONTAS, l.numero(), "codigo", "Código duplicado: " + codigo);
                ok = false;
            }
            if (nome.isEmpty()) {
                ctx.erro(CONTAS, l.numero(), "nome", "Nome vazio");
                ok = false;
            }
            TipoConta tipo = null;
            try {
                tipo = TipoConta.valueOf(l.get("tipo").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                ctx.erro(CONTAS, l.numero(), "tipo", "Tipo desconhecido: '" + l.get("tipo") + "'. Use um de "
                        + EnumSet.allOf(TipoConta.class));
                ok = false;
            }
            Boolean opex = booleano(l.get("opex"));
            if (opex == null) {
                ctx.erro(CONTAS, l.numero(), "opex", "Use S ou N");
                ok = false;
            }
            int ordem = (int) l.numero();
            if (!l.get("ordem").isEmpty()) {
                try {
                    ordem = Integer.parseInt(l.get("ordem"));
                } catch (NumberFormatException e) {
                    ctx.erro(CONTAS, l.numero(), "ordem", "Ordem deve ser um número inteiro");
                    ok = false;
                }
            }
            if (ok) {
                String pai = l.get("codigo_pai").isEmpty() ? null : l.get("codigo_pai");
                contas.put(codigo, new Conta(codigo, nome, pai, tipo, opex, ordem));
                linhaConta.put(codigo, l.numero());
            }
        }
        if (linhas.isEmpty() && ctx.erros.stream().noneMatch(e -> e.arquivo().equals(CONTAS))) {
            ctx.erro(CONTAS, 0, null, "Nenhuma conta informada");
        }
        for (Conta c : contas.values()) {
            String problema = ValidacaoEstrutura.problemaDoPai(c, contas);
            if (problema != null) {
                ctx.erro(CONTAS, linhaConta.get(c.codigo()), "codigo_pai", problema);
            }
        }
        Map<String, Set<TipoConta>> tipos = ValidacaoEstrutura.tiposFolhaPorGrupo(contas);
        for (Conta c : contas.values()) {
            if (c.tipo() != TipoConta.GRUPO) {
                continue;
            }
            Set<TipoConta> t = tipos.getOrDefault(c.codigo(), Set.of());
            if (t.isEmpty()) {
                ctx.aviso(CONTAS, linhaConta.get(c.codigo()), "codigo", "Grupo sem contas filhas: " + c.codigo());
            } else if (t.size() > 1) {
                ctx.erro(CONTAS, linhaConta.get(c.codigo()), "tipo", "Grupo " + c.codigo()
                        + " mistura contas de tipos diferentes " + t + "; um grupo deve ter uma só natureza");
            }
        }
    }

    // ------------------------------------------------------------------ valores

    private List<ValorLancado> lerValores(String conteudo, Contexto ctx, Map<String, Conta> contas, Periodo corte) {
        List<ValorLancado> valores = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        List<Linha> linhas = ler(VALORES, conteudo, List.of("codigo_conta", "ano", "mes", "tipo", "valor"), ctx);
        for (Linha l : linhas) {
            boolean ok = true;
            String codigo = l.get("codigo_conta");
            Conta conta = contas.get(codigo);
            if (conta == null) {
                ctx.erro(VALORES, l.numero(), "codigo_conta", "Conta inexistente: '" + codigo + "'");
                ok = false;
            } else if (!conta.folha()) {
                ctx.erro(VALORES, l.numero(), "codigo_conta",
                        "Conta " + codigo + " é GRUPO; grupos só somam as filhas e não recebem valores");
                ok = false;
            }
            Periodo periodo = null;
            try {
                periodo = new Periodo(Integer.parseInt(l.get("ano")), Integer.parseInt(l.get("mes")));
            } catch (NumberFormatException e) {
                ctx.erro(VALORES, l.numero(), "ano/mes", "Ano e mês devem ser números inteiros");
                ok = false;
            } catch (IllegalArgumentException e) {
                ctx.erro(VALORES, l.numero(), "ano/mes", e.getMessage());
                ok = false;
            }
            TipoValor tipo = null;
            try {
                tipo = TipoValor.valueOf(l.get("tipo").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                ctx.erro(VALORES, l.numero(), "tipo", "Tipo deve ser ORCADO ou REALIZADO");
                ok = false;
            }
            BigDecimal valor = null;
            try {
                valor = numero(l.get("valor"));
                if (valor.signum() < 0) {
                    ctx.erro(VALORES, l.numero(), "valor",
                            "Valor negativo; informe positivo (o sinal vem do tipo da conta)");
                    ok = false;
                }
            } catch (NumberFormatException e) {
                ctx.erro(VALORES, l.numero(), "valor", "Valor não numérico: '" + l.get("valor") + "'");
                ok = false;
            }
            if (ok && tipo == TipoValor.REALIZADO && corte != null && periodo.depoisDe(corte)) {
                ctx.erro(VALORES, l.numero(), "tipo", "REALIZADO em " + periodo
                        + ", mês ainda aberto (data de corte " + corte + ")");
                ok = false;
            }
            if (ok && !vistos.add(codigo + "|" + periodo + "|" + tipo)) {
                ctx.erro(VALORES, l.numero(), null, "Lançamento duplicado: " + codigo + " " + periodo + " " + tipo);
                ok = false;
            }
            if (ok) {
                valores.add(new ValorLancado(codigo, periodo, tipo, valor));
            }
        }
        if (linhas.isEmpty() && ctx.erros.stream().noneMatch(e -> e.arquivo().equals(VALORES))) {
            ctx.erro(VALORES, 0, null, "Nenhum valor informado");
        }
        return valores;
    }

    private void avisarRealizadosFaltando(Contexto ctx, Map<String, Conta> contas, List<ValorLancado> valores,
            Periodo corte) {
        Map<String, Set<Periodo>> orcadoFechado = new HashMap<>();
        Map<String, Set<Periodo>> realizado = new HashMap<>();
        for (ValorLancado v : valores) {
            if (!v.periodo().depoisDe(corte)) {
                (v.tipo() == TipoValor.ORCADO ? orcadoFechado : realizado)
                        .computeIfAbsent(v.codigoConta(), k -> new HashSet<>()).add(v.periodo());
            }
        }
        for (String conta : contas.keySet()) {
            Set<Periodo> faltando = new TreeSet<>(orcadoFechado.getOrDefault(conta, Set.of()));
            faltando.removeAll(realizado.getOrDefault(conta, Set.of()));
            if (!faltando.isEmpty()) {
                List<String> exemplos = faltando.stream().limit(5).map(Periodo::toString).toList();
                ctx.aviso(VALORES, 0, "codigo_conta", "Conta " + conta + ": " + faltando.size()
                        + " mês(es) fechado(s) sem REALIZADO, considerado(s) zero. Ex.: " + exemplos);
            }
        }
    }

    // ------------------------------------------------------------------ premissas

    private Map<String, Premissa> lerPremissas(String conteudo, Contexto ctx) {
        Map<String, String> nomes = new LinkedHashMap<>();
        Map<String, Map<Periodo, BigDecimal>> valores = new HashMap<>();
        for (Linha l : ler(PREMISSAS, conteudo, List.of("codigo", "nome", "ano", "mes", "percentual"), ctx)) {
            String codigo = l.get("codigo");
            if (codigo.isEmpty()) {
                ctx.erro(PREMISSAS, l.numero(), "codigo", "Código vazio");
                continue;
            }
            String nome = l.get("nome").isEmpty() ? codigo : l.get("nome");
            String nomeAnterior = nomes.putIfAbsent(codigo, nome);
            if (nomeAnterior != null && !nomeAnterior.equals(nome)) {
                ctx.aviso(PREMISSAS, l.numero(), "nome", "Premissa " + codigo + " com nomes diferentes; usando '"
                        + nomeAnterior + "'");
            }
            Periodo periodo;
            try {
                periodo = new Periodo(Integer.parseInt(l.get("ano")), Integer.parseInt(l.get("mes")));
            } catch (NumberFormatException e) {
                ctx.erro(PREMISSAS, l.numero(), "ano/mes", "Ano e mês devem ser números inteiros");
                continue;
            } catch (IllegalArgumentException e) {
                ctx.erro(PREMISSAS, l.numero(), "ano/mes", e.getMessage());
                continue;
            }
            BigDecimal pct;
            try {
                pct = numero(l.get("percentual"));
            } catch (NumberFormatException e) {
                ctx.erro(PREMISSAS, l.numero(), "percentual", "Percentual não numérico: '" + l.get("percentual") + "'");
                continue;
            }
            String problema = ValidacaoEstrutura.problemaPercentual(pct);
            if (problema != null) {
                ctx.erro(PREMISSAS, l.numero(), "percentual", problema);
                continue;
            }
            if (valores.computeIfAbsent(codigo, k -> new TreeMap<>()).put(periodo, pct) != null) {
                ctx.erro(PREMISSAS, l.numero(), null, "Premissa duplicada: " + codigo + " " + periodo);
            }
        }
        Map<String, Premissa> premissas = new LinkedHashMap<>();
        nomes.forEach((codigo, nome) -> premissas.put(codigo,
                Premissa.de(codigo, nome, valores.getOrDefault(codigo, Map.of()))));
        return premissas;
    }

    // ------------------------------------------------------------------ vínculos

    private List<Vinculo> lerVinculos(String conteudo, Contexto ctx, Map<String, Conta> contas,
            Map<String, Premissa> premissas) {
        List<Vinculo> vinculos = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (Linha l : ler(VINCULOS, conteudo, List.of("codigo_conta", "codigo_premissa", "vigencia_inicio"), ctx)) {
            boolean ok = true;
            String conta = l.get("codigo_conta");
            String premissa = l.get("codigo_premissa");
            Conta c = contas.get(conta);
            if (c == null) {
                ctx.erro(VINCULOS, l.numero(), "codigo_conta", "Conta inexistente: '" + conta + "'");
                ok = false;
            } else if (!c.folha()) {
                ctx.erro(VINCULOS, l.numero(), "codigo_conta", "Conta " + conta + " é GRUPO; vincule as contas filhas");
                ok = false;
            }
            if (!premissas.containsKey(premissa)) {
                ctx.erro(VINCULOS, l.numero(), "codigo_premissa", "Premissa inexistente: '" + premissa + "'");
                ok = false;
            }
            Periodo inicio = null;
            try {
                inicio = Periodo.parse(l.get("vigencia_inicio"));
            } catch (IllegalArgumentException e) {
                ctx.erro(VINCULOS, l.numero(), "vigencia_inicio", e.getMessage());
                ok = false;
            }
            if (ok && !vistos.add(conta + "|" + premissa)) {
                ctx.erro(VINCULOS, l.numero(), null, "Vínculo duplicado: " + conta + " → " + premissa);
                ok = false;
            }
            if (ok) {
                vinculos.add(new Vinculo(conta, premissa, inicio));
            }
        }
        return vinculos;
    }
}
