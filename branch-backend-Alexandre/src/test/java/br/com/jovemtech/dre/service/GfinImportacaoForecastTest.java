package br.com.jovemtech.dre.service;

import br.com.jovemtech.dre.model.*;
import br.com.jovemtech.dre.repository.*;
import java.math.BigDecimal;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;

/** Testes com CSV extraído da fonte GFIN, sem carregar a planilha no servidor. */
class GfinImportacaoForecastTest {
    @Test void importaBaseGfinECalculaEbitdaAssinadoComCenariosIndependentes() throws Exception {
        byte[] dados;
        try (InputStream in = GfinImportacaoForecastTest.class.getResourceAsStream("/fixtures/gfin-importacao-2025-2026.csv")) {
            assertNotNull(in, "Fixture GFIN ausente");
            dados = in.readAllBytes();
        }
        var arquivo=new MockMultipartFile("arquivo","gfin.csv","text/csv",dados);
        var repositorio=new InMemoryDreRepository();
        var importador=new DreImportService(repositorio);
        assertEquals(2256,importador.importar(arquivo,true));
        assertEquals(2256,repositorio.listar().size());
        var servico=new CenarioService(new InMemoryCenarioRepository(),repositorio);
        var primeiro=servico.criar("Cenário GFIN A",2026,8);
        var segundo=servico.criar("Cenário GFIN B",2026,8);
        var antesA=servico.calcular(primeiro.id());
        var antesB=servico.calcular(segundo.id());
        assertEquals("COMPLETO",antesA.meses().get(7).status());
        assertEquals("COMPLETO",antesA.meses().get(8).status());
        assertEquals("COMPLETO",antesA.meses().get(11).statusOrcamento());
        assertEquals("GFIN_DESPESAS_NEGATIVAS",antesA.meses().get(8).convencaoSinais());
        assertTrue(antesA.meses().get(8).despesas().signum()<0);
        assertTrue(antesA.meses().get(8).deducoes().signum()<0);
        assertTrue(antesA.meses().get(8).receitaLiquida().signum()>0);
        // Março orçado divergente do subtotal da planilha; calculado com contas analíticas.
        assertTrue(antesA.meses().get(2).ebitdaOrcamento()
            .subtract(new BigDecimal("14453.46189321")).abs().compareTo(new BigDecimal("0.01"))<0);
        assertTrue(antesA.meses().get(11).ebitdaOrcamento()
            .subtract(new BigDecimal("-67660.09627008")).abs().compareTo(new BigDecimal("0.01"))<0);
        // Alterar cenário A não afeta os períodos realizados nem outro cenário.
        servico.alterarVariacao(primeiro.id(),"RECEITA",new BigDecimal("10"));
        var depoisA=servico.calcular(primeiro.id());
        var depoisB=servico.calcular(segundo.id());
        assertEquals(0,depoisA.meses().get(7).ebitdaDemonstrativo().compareTo(antesA.meses().get(7).ebitdaDemonstrativo()));
        assertTrue(depoisA.meses().get(8).ebitdaDemonstrativo().compareTo(antesA.meses().get(8).ebitdaDemonstrativo())>0);
        assertEquals(0,depoisB.meses().get(8).ebitdaDemonstrativo().compareTo(antesB.meses().get(8).ebitdaDemonstrativo()));
        assertThrows(RegraNegocioException.class,()->servico.definirAjuste(primeiro.id(),"GFIN-008",8,new BigDecimal("5")));
        assertThrows(RegraNegocioException.class,()->servico.criar("Fechamento não informado",2026,9));
        // Substituir o mesmo CSV não duplica registros.
        assertEquals(2256,importador.importar(arquivo,true));
        assertEquals(2256,repositorio.listar().size());
    }
}
