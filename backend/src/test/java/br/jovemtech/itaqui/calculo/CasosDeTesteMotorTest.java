package br.jovemtech.itaqui.calculo;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.jovemtech.itaqui.calculo.ResultadoDre.LinhaDre;
import br.jovemtech.itaqui.dominio.Base;
import br.jovemtech.itaqui.dominio.Cenario;
import br.jovemtech.itaqui.dominio.Periodo;
import br.jovemtech.itaqui.dominio.StatusPeriodo;

import static br.jovemtech.itaqui.calculo.BaseTeste.ANO;
import static br.jovemtech.itaqui.calculo.BaseTeste.bd;
import static org.assertj.core.api.Assertions.assertThat;

/** CT-01 a CT-06 do PRD, sobre a {@link BaseTeste} (valores conferidos à mão). */
class CasosDeTesteMotorTest {

    private final MotorCalculo motor = new MotorCalculo(new ProjecaoPorIndice());
    private final Base base = BaseTeste.criar();

    private static LinhaDre linha(ResultadoDre r, String codigo) {
        return r.linhas().stream().filter(l -> l.codigo().equals(codigo)).findFirst().orElseThrow();
    }

    private static Cenario cenarioComIpca(String pct) {
        Map<Periodo, BigDecimal> ajustes = new HashMap<>();
        for (int m = 7; m <= 12; m++) {
            ajustes.put(new Periodo(ANO, m), bd(pct));
        }
        Cenario c = new Cenario("teste", "IPCA alto", false, java.time.Instant.now(), java.time.Instant.now(),
                Map.of());
        return c.comAjustes("IPCA", ajustes);
    }

    @Test
    @DisplayName("CT-01 Cálculo base: EBITDA e margem conferem com o cálculo manual")
    void ct01CalculoBase() {
        ResultadoDre r = motor.calcular(base, Cenario.original(), ANO);
        var ind = r.indicadores();
        assertThat(ind.receitaBruta().orcado()).isEqualByComparingTo("12000");
        assertThat(ind.receitaLiquida().orcado()).isEqualByComparingTo("10800");
        assertThat(ind.ebitda().orcado()).isEqualByComparingTo("4200");
        assertThat(ind.margemEbitdaPct().orcado()).isEqualByComparingTo("38.89");
        assertThat(ind.ebitda().realizado()).isEqualByComparingTo("2610");
        assertThat(ind.depreciacaoAmortizacao().orcado()).isEqualByComparingTo("480");
        // D&A não entra no EBITDA
        assertThat(r.indicadoresMensais().get(0).indicadores().ebitda().orcado()).isEqualByComparingTo("350");
        // Grupo soma as filhas
        assertThat(linha(r, "C").total().orcado()).isEqualByComparingTo("4200");
    }

    @Test
    @DisplayName("CT-02 Forecast = realizado dos meses fechados + projeção dos abertos")
    void ct02Forecast() {
        ResultadoDre r = motor.calcular(base, Cenario.original(), ANO);
        assertThat(r.indicadores().ebitda().forecast()).isEqualByComparingTo("4710");
        LinhaDre r1 = linha(r, "R1");
        for (var m : r1.meses()) {
            if (m.mes() <= 6) {
                assertThat(m.status()).isEqualTo(StatusPeriodo.FECHADO);
                assertThat(m.forecast()).isEqualByComparingTo(m.realizado());
                assertThat(m.projetado()).isNull();
            } else {
                assertThat(m.status()).isEqualTo(StatusPeriodo.ABERTO);
                assertThat(m.forecast()).isEqualByComparingTo(m.projetado());
                assertThat(m.realizado()).isNull();
            }
        }
        assertThat(r1.total().forecast())
                .isEqualByComparingTo(r1.total().realizado().add(r1.total().projetado()));
        assertThat(r1.total().orcadoAteCorte()).isEqualByComparingTo("6000");
    }

    @Test
    @DisplayName("CT-03 Recálculo por premissa: só contas vinculadas e meses abertos mudam")
    void ct03RecalculoPorPremissa() {
        ResultadoDre antes = motor.calcular(base, Cenario.original(), ANO);
        ResultadoDre depois = motor.calcular(base, cenarioComIpca("10.0"), ANO);

        // C1 = 300 × 1,10 / 1,045 = 315,789473...
        var c1 = linha(depois, "C1").meses();
        for (int i = 6; i < 12; i++) {
            assertThat(c1.get(i).projetado()).isEqualByComparingTo("315.79");
        }
        // Meses fechados de C1 não mudam
        for (int i = 0; i < 6; i++) {
            assertThat(c1.get(i)).isEqualTo(linha(antes, "C1").meses().get(i));
        }
        // Contas sem vínculo com IPCA não mudam
        for (String conta : new String[] { "R1", "D1", "C2", "E1", "DA" }) {
            assertThat(linha(depois, conta)).isEqualTo(linha(antes, conta));
        }
        // 2610 + 6 × (350 − 15,789473...) = 4615,26
        assertThat(depois.indicadores().ebitda().forecast()).isEqualByComparingTo("4615.26");
        assertThat(depois.indicadores().ebitda().orcado()).isEqualByComparingTo("4200");
    }

    @Test
    @DisplayName("CT-03b Vigência: o vínculo só vale a partir do mês de início")
    void ct03Vigencia() {
        Base comVigencia = BaseTeste.criar(new Periodo(2026, 10));
        var c1 = linha(motor.calcular(comVigencia, cenarioComIpca("10.0"), ANO), "C1").meses();
        assertThat(c1.get(6).projetado()).isEqualByComparingTo("300");  // jul
        assertThat(c1.get(8).projetado()).isEqualByComparingTo("300");  // set
        assertThat(c1.get(9).projetado()).isEqualByComparingTo("315.79"); // out
    }

    @Test
    @DisplayName("CT-04 Conta sem premissa: projeção igual ao orçado (RN005)")
    void ct04ContaSemPremissa() {
        ResultadoDre r = motor.calcular(base, cenarioComIpca("25.0"), ANO);
        for (String conta : new String[] { "C2", "E1", "D1" }) {
            for (var m : linha(r, conta).meses()) {
                if (m.status() == StatusPeriodo.ABERTO) {
                    assertThat(m.projetado()).isEqualByComparingTo(m.orcado());
                }
            }
        }
    }

    @Test
    @DisplayName("CT-04b No cenário original, projetado = orçado em todas as contas")
    void ct04OriginalIgualOrcado() {
        ResultadoDre r = motor.calcular(base, Cenario.original(), ANO);
        r.linhas().forEach(l -> l.meses().stream().filter(m -> m.status() == StatusPeriodo.ABERTO)
                .forEach(m -> assertThat(m.projetado()).isEqualByComparingTo(m.orcado())));
    }

    @Test
    @DisplayName("CT-05 Cenário isolado: o orçamento original não muda")
    void ct05CenarioIsolado() {
        ResultadoDre antes = motor.calcular(base, Cenario.original(), ANO);
        Cenario simulado = cenarioComIpca("12.0");
        motor.calcular(base, simulado, ANO);
        ResultadoDre depois = motor.calcular(base, Cenario.original(), ANO);
        assertThat(depois.linhas()).isEqualTo(antes.linhas());
        assertThat(depois.indicadores()).isEqualTo(antes.indicadores());
        // O orçado do próprio cenário simulado também continua o original
        ResultadoDre sim = motor.calcular(base, simulado, ANO);
        assertThat(sim.indicadores().ebitda().orcado()).isEqualByComparingTo("4200");
        assertThat(sim.indicadores().ebitda().realizado()).isEqualByComparingTo("2610");
    }

    @Test
    @DisplayName("CT-06 Desempenho: recálculo completo bem abaixo de 3 s")
    void ct06Desempenho() {
        Cenario c = cenarioComIpca("9.9");
        motor.calcular(base, c, ANO); // aquecimento
        long inicio = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            motor.calcular(base, c, ANO);
        }
        double msPorCalculo = (System.nanoTime() - inicio) / 1_000_000.0 / 100;
        assertThat(msPorCalculo).isLessThan(3000);
    }

    @Test
    @DisplayName("Duas premissas na mesma conta: os fatores se multiplicam")
    void duasPremissas() {
        var regra = new ProjecaoPorIndice();
        BigDecimal r = regra.projetar(bd("100"), java.util.List.of(
                new IndiceAplicado("A", bd("0"), bd("10")),
                new IndiceAplicado("B", bd("0"), bd("10"))));
        assertThat(r).isEqualByComparingTo("121");
    }
}
