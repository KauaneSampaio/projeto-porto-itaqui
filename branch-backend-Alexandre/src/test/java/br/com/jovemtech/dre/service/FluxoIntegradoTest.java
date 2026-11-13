package br.com.jovemtech.dre.service;

import br.com.jovemtech.dre.model.*;
import br.com.jovemtech.dre.repository.*;
import java.math.BigDecimal;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;

class FluxoIntegradoTest {
    @Test void demonstraForecastCompletoComDoisCenariosEOrcamento() throws Exception {
        var dre=new InMemoryDreRepository();
        var importar=new DreImportService(dre);
        byte[] bytes;
        try (InputStream in = FluxoIntegradoTest.class.getResourceAsStream("/fixtures/dre-demo-completa.csv")) {
            assertNotNull(in, "Fixture de teste ausente");
            bytes = in.readAllBytes();
        }
        var arquivo=new MockMultipartFile("arquivo","dre-demo.csv","text/csv",bytes);
        assertEquals(72,importar.importar(arquivo));
        var servico=new CenarioService(new InMemoryCenarioRepository(),dre);
        var a=servico.criar("Otimista",2026,2);
        var b=servico.criar("Base",2026,2);
        servico.alterarVariacao(a.id(),"RECEITA",BigDecimal.TEN);
        var previsaoA=servico.calcular(a.id());
        var previsaoB=servico.calcular(b.id());
        assertEquals(0,previsaoA.linhas().stream()
            .filter(l->l.contaId().equals("REC01")&&l.mes()==3)
            .findFirst().orElseThrow().valorForecast().compareTo(new BigDecimal("110660.00")));
        assertEquals(0,previsaoB.linhas().stream()
            .filter(l->l.contaId().equals("REC01")&&l.mes()==3)
            .findFirst().orElseThrow().valorForecast().compareTo(new BigDecimal("100600.00")));
        var resumo=previsaoA.meses().get(2);
        assertEquals("COMPLETO",resumo.status());
        assertEquals("COMPLETO",resumo.statusOrcamento());
        assertEquals(0,resumo.ebitdaDemonstrativo().compareTo(new BigDecimal("52460.00")));
        assertEquals(0,resumo.ebitdaOrcamento().compareTo(new BigDecimal("42700.00")));
        assertEquals(0,resumo.desvioEbitda().compareTo(new BigDecimal("9760.00")));
        assertThrows(ImportacaoException.class,()->importar.importar(arquivo));
        assertEquals(72,dre.listar().size());
    }
}
