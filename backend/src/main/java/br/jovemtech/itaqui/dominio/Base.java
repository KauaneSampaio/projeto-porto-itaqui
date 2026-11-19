package br.jovemtech.itaqui.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Base de dados carregada (estrutura da DRE, orçado, realizado, premissas e vínculos).
 *
 * <p>É <b>imutável</b>: qualquer alteração gera uma nova instância. Por isso nenhum
 * cenário consegue sobrescrever o orçamento original ou o realizado de um mês fechado.
 */
public final class Base {

    public static final String ORIGEM_FICTICIO = "FICTICIO";

    private record ChaveValor(String conta, Periodo periodo, TipoValor tipo) {
    }

    private static final Comparator<Conta> ORDEM =
            Comparator.comparingInt(Conta::ordem).thenComparing(Conta::codigo);

    private final Map<String, Conta> contas;
    private final Map<String, List<Conta>> filhos;
    private final List<Conta> raizes;
    private final List<ValorLancado> valores;
    private final Map<ChaveValor, BigDecimal> indiceValores;
    private final Map<String, Premissa> premissas;
    private final List<Vinculo> vinculos;
    private final Map<String, List<Vinculo>> vinculosPorConta;
    private final SortedSet<Integer> anos;
    private final Periodo dataCorte;
    private final String origem;
    private final String unidade;
    private final int versao;
    private final String fonte;
    private final Instant carregadaEm;

    public Base(List<Conta> contas, List<ValorLancado> valores, List<Premissa> premissas, List<Vinculo> vinculos,
            Periodo dataCorte, String origem, String unidade, int versao, String fonte, Instant carregadaEm) {
        List<Conta> ordenadas = new ArrayList<>(contas);
        ordenadas.sort(ORDEM);
        Map<String, Conta> mapaContas = new LinkedHashMap<>();
        Map<String, List<Conta>> mapaFilhos = new HashMap<>();
        List<Conta> listaRaizes = new ArrayList<>();
        for (Conta c : ordenadas) {
            mapaContas.put(c.codigo(), c);
            if (c.codigoPai() == null) {
                listaRaizes.add(c);
            } else {
                mapaFilhos.computeIfAbsent(c.codigoPai(), k -> new ArrayList<>()).add(c);
            }
        }
        this.contas = Collections.unmodifiableMap(mapaContas);
        mapaFilhos.replaceAll((k, v) -> List.copyOf(v));
        this.filhos = Collections.unmodifiableMap(mapaFilhos);
        this.raizes = List.copyOf(listaRaizes);

        this.valores = List.copyOf(valores);
        Map<ChaveValor, BigDecimal> idx = new HashMap<>();
        SortedSet<Integer> setAnos = new TreeSet<>();
        for (ValorLancado v : valores) {
            idx.put(new ChaveValor(v.codigoConta(), v.periodo(), v.tipo()), v.valor());
            setAnos.add(v.periodo().ano());
        }
        this.indiceValores = Collections.unmodifiableMap(idx);
        this.anos = Collections.unmodifiableSortedSet(setAnos);

        Map<String, Premissa> mapaPremissas = new LinkedHashMap<>();
        premissas.stream().sorted(Comparator.comparing(Premissa::codigo))
                .forEach(p -> mapaPremissas.put(p.codigo(), p));
        this.premissas = Collections.unmodifiableMap(mapaPremissas);

        this.vinculos = List.copyOf(vinculos);
        Map<String, List<Vinculo>> porConta = new HashMap<>();
        for (Vinculo v : vinculos) {
            porConta.computeIfAbsent(v.codigoConta(), k -> new ArrayList<>()).add(v);
        }
        porConta.replaceAll((k, v) -> List.copyOf(v));
        this.vinculosPorConta = Collections.unmodifiableMap(porConta);

        this.dataCorte = dataCorte;
        this.origem = origem;
        this.unidade = unidade;
        this.versao = versao;
        this.fonte = fonte;
        this.carregadaEm = carregadaEm;
    }

    // ---------------------------------------------------------------- consultas

    public Periodo dataCorte() {
        return dataCorte;
    }

    public boolean fechado(Periodo periodo) {
        return !periodo.depoisDe(dataCorte);
    }

    public StatusPeriodo status(Periodo periodo) {
        return fechado(periodo) ? StatusPeriodo.FECHADO : StatusPeriodo.ABERTO;
    }

    /** Valor carregado ou {@code null} se não houver lançamento. */
    public BigDecimal valor(String codigoConta, Periodo periodo, TipoValor tipo) {
        return indiceValores.get(new ChaveValor(codigoConta, periodo, tipo));
    }

    public boolean temValores(String codigoConta) {
        return valores.stream().anyMatch(v -> v.codigoConta().equals(codigoConta));
    }

    public Optional<Conta> conta(String codigo) {
        return Optional.ofNullable(contas.get(codigo));
    }

    public List<Conta> contas() {
        return List.copyOf(contas.values());
    }

    public List<Conta> raizes() {
        return raizes;
    }

    public List<Conta> filhos(String codigo) {
        return filhos.getOrDefault(codigo, List.of());
    }

    public List<ValorLancado> valores() {
        return valores;
    }

    public Optional<Premissa> premissa(String codigo) {
        return Optional.ofNullable(premissas.get(codigo));
    }

    public List<Premissa> premissas() {
        return List.copyOf(premissas.values());
    }

    public List<Vinculo> vinculos() {
        return vinculos;
    }

    public List<Vinculo> vinculos(String codigoConta) {
        return vinculosPorConta.getOrDefault(codigoConta, List.of());
    }

    public SortedSet<Integer> anos() {
        return anos;
    }

    public String origem() {
        return origem;
    }

    public String unidade() {
        return unidade;
    }

    public int versao() {
        return versao;
    }

    public String fonte() {
        return fonte;
    }

    public Instant carregadaEm() {
        return carregadaEm;
    }

    // ---------------------------------------------------------------- cópias alteradas

    private Base copia(List<Conta> c, List<Premissa> p, List<Vinculo> v, String novaFonte) {
        return new Base(c, valores, p, v, dataCorte, origem, unidade, versao, novaFonte, Instant.now());
    }

    public Base comFonte(String novaFonte) {
        return copia(contas(), premissas(), vinculos, novaFonte);
    }

    public Base comConta(Conta conta) {
        List<Conta> novas = new ArrayList<>(contas.values());
        novas.add(conta);
        return copia(novas, premissas(), vinculos, fonte);
    }

    public Base semConta(String codigo) {
        List<Conta> novas = new ArrayList<>(contas.values());
        novas.removeIf(c -> c.codigo().equals(codigo));
        return copia(novas, premissas(), vinculos, fonte);
    }

    public Base comPremissa(Premissa premissa) {
        List<Premissa> novas = new ArrayList<>(premissas.values());
        novas.removeIf(p -> p.codigo().equals(premissa.codigo()));
        novas.add(premissa);
        return copia(contas(), novas, vinculos, fonte);
    }

    public Base comVinculo(Vinculo vinculo) {
        List<Vinculo> novos = new ArrayList<>(vinculos);
        novos.add(vinculo);
        return copia(contas(), premissas(), novos, fonte);
    }

    public Base semVinculos(Predicate<Vinculo> filtro) {
        List<Vinculo> novos = new ArrayList<>(vinculos);
        novos.removeIf(filtro);
        return copia(contas(), premissas(), novos, fonte);
    }
}
