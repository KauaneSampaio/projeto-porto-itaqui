package br.jovemtech.itaqui.servico;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import br.jovemtech.itaqui.api.ApiException;
import br.jovemtech.itaqui.api.dto.CenarioResposta;
import br.jovemtech.itaqui.api.dto.ComparativoResposta;
import br.jovemtech.itaqui.api.dto.ComparativoResposta.Comparado;
import br.jovemtech.itaqui.api.dto.ComparativoResposta.IndicadorComparado;
import br.jovemtech.itaqui.api.dto.ComparativoResposta.LinhaComparada;
import br.jovemtech.itaqui.api.dto.ComparativoResposta.MesComparado;
import br.jovemtech.itaqui.api.dto.DesempenhoResposta;
import br.jovemtech.itaqui.api.dto.PercentualPeriodo;
import br.jovemtech.itaqui.api.dto.PremissaCenarioResposta;
import br.jovemtech.itaqui.calculo.MotorCalculo;
import br.jovemtech.itaqui.calculo.ResultadoDre;
import br.jovemtech.itaqui.calculo.ResultadoDre.Indicadores;
import br.jovemtech.itaqui.calculo.ResultadoDre.LinhaDre;
import br.jovemtech.itaqui.calculo.ResultadoDre.Perspectivas;
import br.jovemtech.itaqui.calculo.ResultadoDre.ValoresMes;
import br.jovemtech.itaqui.dados.RepositorioCenarios;
import br.jovemtech.itaqui.dados.ValidacaoEstrutura;
import br.jovemtech.itaqui.dominio.Base;
import br.jovemtech.itaqui.dominio.Cenario;
import br.jovemtech.itaqui.dominio.Periodo;
import br.jovemtech.itaqui.dominio.Premissa;
import br.jovemtech.itaqui.dominio.Vinculo;

/** Cenários de simulação e os cálculos sobre eles (RF003, RF005–RF009). */
@Service
public class ServicoCenario {

    /** Meta do RNF002 / CT-06. */
    public static final double META_MS = 3000.0;

    private final RepositorioCenarios repositorio;
    private final ServicoBase servicoBase;
    private final MotorCalculo motor;

    public ServicoCenario(RepositorioCenarios repositorio, ServicoBase servicoBase, MotorCalculo motor) {
        this.repositorio = repositorio;
        this.servicoBase = servicoBase;
        this.motor = motor;
    }

    // ------------------------------------------------------------------ CRUD

    public List<CenarioResposta> listar() {
        return repositorio.listar().stream().map(ServicoCenario::resposta).toList();
    }

    public CenarioResposta detalhe(String id) {
        return resposta(buscar(id));
    }

    public Cenario buscar(String id) {
        return repositorio.buscar(id).orElseThrow(() -> ApiException.naoEncontrado("Cenário inexistente: " + id));
    }

    public synchronized CenarioResposta criar(String nome, String copiarDe) {
        String nomeLimpo = nome.trim();
        exigirNomeLivre(nomeLimpo, null);
        Map<String, SortedMap<Periodo, BigDecimal>> ajustes = Map.of();
        if (copiarDe != null && !copiarDe.isBlank()) {
            ajustes = buscar(copiarDe.trim()).ajustes();
        }
        Instant agora = Instant.now();
        String id = "cen-" + UUID.randomUUID().toString().substring(0, 8);
        Cenario novo = new Cenario(id, nomeLimpo, false, agora, agora, ajustes);
        repositorio.salvar(novo);
        return resposta(novo);
    }

    public synchronized CenarioResposta renomear(String id, String nome) {
        Cenario c = editavel(id);
        exigirNomeLivre(nome.trim(), id);
        Cenario renomeado = c.comNome(nome.trim());
        repositorio.salvar(renomeado);
        return resposta(renomeado);
    }

    public synchronized void remover(String id) {
        editavel(id);
        repositorio.remover(id);
    }

    private void exigirNomeLivre(String nome, String idAtual) {
        String chave = nome.toLowerCase(Locale.ROOT);
        boolean emUso = repositorio.listar().stream()
                .anyMatch(c -> !c.id().equals(idAtual) && c.nome().toLowerCase(Locale.ROOT).equals(chave));
        if (emUso) {
            throw ApiException.conflito("NOME_EM_USO", "Já existe um cenário chamado '" + nome + "'");
        }
    }

    private Cenario editavel(String id) {
        Cenario c = buscar(id);
        if (c.somenteLeitura()) {
            throw ApiException.conflito("CENARIO_SOMENTE_LEITURA",
                    "O orçamento original não pode ser alterado (RN006). Crie um cenário para simular.");
        }
        return c;
    }

    // ------------------------------------------------------------------ premissas do cenário

    /**
     * Altera percentuais de uma premissa no cenário. Só meses abertos: o realizado dos
     * meses fechados nunca muda (RN003).
     */
    public synchronized CenarioResposta ajustarPremissa(String id, String codigoPremissa,
            List<PercentualPeriodo> ajustes) {
        Cenario c = editavel(id);
        Base base = servicoBase.base();
        if (base.premissa(codigoPremissa).isEmpty()) {
            throw ApiException.naoEncontrado("Premissa inexistente: " + codigoPremissa);
        }
        Map<Periodo, BigDecimal> novos = new TreeMap<>();
        List<String> erros = new ArrayList<>();
        for (PercentualPeriodo a : ajustes) {
            Periodo p = a.periodo();
            if (base.fechado(p)) {
                erros.add(p + ": mês fechado (data de corte " + base.dataCorte() + "); o realizado não é alterado");
                continue;
            }
            if (!base.anos().contains(p.ano())) {
                erros.add(p + ": ano sem dados na base " + base.anos());
                continue;
            }
            String problema = ValidacaoEstrutura.problemaPercentual(a.percentual());
            if (problema != null) {
                erros.add(p + ": " + problema);
                continue;
            }
            if (novos.put(p, a.percentual()) != null) {
                erros.add(p + ": período repetido na requisição");
            }
        }
        if (!erros.isEmpty()) {
            boolean soFechado = erros.stream().allMatch(e -> e.contains("mês fechado"));
            throw ApiException.regra(soFechado ? "PERIODO_FECHADO" : "AJUSTE_INVALIDO",
                    "Ajuste recusado; nada foi alterado", erros);
        }
        Cenario atualizado = c.comAjustes(codigoPremissa, novos);
        repositorio.salvar(atualizado);
        return resposta(atualizado);
    }

    public synchronized CenarioResposta removerAjustes(String id, String codigoPremissa) {
        Cenario c = editavel(id);
        Cenario atualizado = c.semAjustes(codigoPremissa);
        repositorio.salvar(atualizado);
        return resposta(atualizado);
    }

    public List<PremissaCenarioResposta> premissasDoCenario(String id) {
        Cenario c = buscar(id);
        Base base = servicoBase.base();
        List<PremissaCenarioResposta> lista = new ArrayList<>();
        for (Premissa p : base.premissas()) {
            SortedSet<Periodo> periodos = new TreeSet<>(p.percentuais().keySet());
            periodos.addAll(c.ajustes().getOrDefault(p.codigo(), new TreeMap<>()).keySet());
            List<PremissaCenarioResposta.Valor> valores = new ArrayList<>();
            for (Periodo per : periodos) {
                BigDecimal original = p.percentual(per);
                BigDecimal noCenario = c.percentual(p, per);
                valores.add(new PremissaCenarioResposta.Valor(per, base.status(per), original, noCenario,
                        original.compareTo(noCenario) != 0));
            }
            List<String> contas = base.vinculos().stream().filter(v -> v.codigoPremissa().equals(p.codigo()))
                    .map(Vinculo::codigoConta).toList();
            lista.add(new PremissaCenarioResposta(p.codigo(), p.nome(), contas, valores));
        }
        return lista;
    }

    // ------------------------------------------------------------------ cálculos

    public ResultadoDre dre(String id, int ano) {
        Cenario c = buscar(id);
        servicoBase.exigirAno(ano);
        return motor.calcular(servicoBase.base(), c, ano);
    }

    public ComparativoResposta comparativo(String id, int ano, String baseId) {
        Cenario cenario = buscar(id);
        Cenario referencia = buscar(baseId);
        servicoBase.exigirAno(ano);
        Base base = servicoBase.base();
        ResultadoDre rc = motor.calcular(base, cenario, ano);
        ResultadoDre rb = motor.calcular(base, referencia, ano);

        Map<String, LinhaDre> linhasBase = new LinkedHashMap<>();
        rb.linhas().forEach(l -> linhasBase.put(l.codigo(), l));
        List<LinhaComparada> linhas = new ArrayList<>();
        int alteradas = 0;
        for (LinhaDre lc : rc.linhas()) {
            LinhaDre lb = linhasBase.get(lc.codigo());
            List<MesComparado> meses = new ArrayList<>();
            boolean alterada = false;
            for (int i = 0; i < lc.meses().size(); i++) {
                ValoresMes mc = lc.meses().get(i);
                ValoresMes mb = lb.meses().get(i);
                Comparado comp = comparar(mb.forecast(), mc.forecast(), true);
                alterada |= comp.diferenca().signum() != 0;
                meses.add(new MesComparado(mc.mes(), mc.status(), comp.base(), comp.cenario(), comp.diferenca(),
                        comp.diferencaPct()));
            }
            if (alterada) {
                alteradas++;
            }
            linhas.add(new LinhaComparada(lc.codigo(), lc.nome(), lc.nivel(), lc.folha(), lc.opex(), alterada,
                    comparar(lb.total().forecast(), lc.total().forecast(), true), meses));
        }

        List<IndicadorComparado> indicadores = new ArrayList<>();
        Indicadores ib = rb.indicadores();
        Indicadores ic = rc.indicadores();
        indicadores.add(indicador("receitaLiquida", ib, ic, Indicadores::receitaLiquida, true));
        indicadores.add(indicador("custos", ib, ic, Indicadores::custos, true));
        indicadores.add(indicador("despesasOperacionais", ib, ic, Indicadores::despesasOperacionais, true));
        indicadores.add(indicador("ebitda", ib, ic, Indicadores::ebitda, true));
        indicadores.add(indicador("margemEbitdaPct", ib, ic, Indicadores::margemEbitdaPct, false));

        return new ComparativoResposta(cenario.id(), cenario.nome(), referencia.id(), referencia.nome(), ano,
                base.dataCorte(), base.origem(), alteradas, indicadores, linhas,
                rc.tempoCalculoMs() + rb.tempoCalculoMs());
    }

    private static IndicadorComparado indicador(String nome, Indicadores base, Indicadores cenario,
            Function<Indicadores, Perspectivas> campo, boolean comPct) {
        return new IndicadorComparado(nome,
                comparar(campo.apply(base).forecast(), campo.apply(cenario).forecast(), comPct));
    }

    private static Comparado comparar(BigDecimal base, BigDecimal cenario, boolean comPct) {
        BigDecimal b = base == null ? BigDecimal.ZERO : base;
        BigDecimal c = cenario == null ? BigDecimal.ZERO : cenario;
        BigDecimal dif = c.subtract(b);
        BigDecimal pct = null;
        if (comPct && b.signum() != 0) {
            pct = dif.multiply(BigDecimal.valueOf(100)).divide(b.abs(), MathContext.DECIMAL128)
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return new Comparado(base, cenario, dif, pct);
    }

    public DesempenhoResposta desempenho(String id, int ano, int repeticoes) {
        Cenario c = buscar(id);
        servicoBase.exigirAno(ano);
        Base base = servicoBase.base();
        int n = Math.max(1, Math.min(repeticoes, 200));
        double min = Double.MAX_VALUE;
        double max = 0;
        double soma = 0;
        for (int i = 0; i < n; i++) {
            long t0 = System.nanoTime();
            motor.calcular(base, c, ano);
            double ms = (System.nanoTime() - t0) / 1_000_000.0;
            min = Math.min(min, ms);
            max = Math.max(max, ms);
            soma += ms;
        }
        return new DesempenhoResposta(c.id(), ano, n, arred(min), arred(soma / n), arred(max), META_MS,
                max < META_MS);
    }

    private static double arred(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    // ------------------------------------------------------------------

    static CenarioResposta resposta(Cenario c) {
        Map<String, List<PercentualPeriodo>> ajustes = new LinkedHashMap<>();
        int meses = 0;
        for (Map.Entry<String, SortedMap<Periodo, BigDecimal>> e : c.ajustes().entrySet()) {
            List<PercentualPeriodo> lista = e.getValue().entrySet().stream()
                    .map(x -> new PercentualPeriodo(x.getKey(), x.getValue())).toList();
            meses += lista.size();
            ajustes.put(e.getKey(), lista);
        }
        long premissas = c.ajustes().values().stream().filter(Objects::nonNull).filter(m -> !m.isEmpty()).count();
        return new CenarioResposta(c.id(), c.nome(), c.somenteLeitura(), c.criadoEm(), c.atualizadoEm(),
                (int) premissas, meses, ajustes);
    }
}
