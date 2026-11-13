package br.com.jovemtech.dre.service;

import br.com.jovemtech.dre.model.*;
import br.com.jovemtech.dre.repository.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class CenarioServiceTest {
    private InMemoryDreRepository dre;
    private CenarioService service;

    @BeforeEach void preparar() {
        dre=new InMemoryDreRepository();
        service=new CenarioService(new InMemoryCenarioRepository(),dre);
        List<DreRegistro> dados=new ArrayList<>();
        for(String grupo:List.of("RECEITA","CUSTO","OPEX")) {
            String conta=grupo.substring(0,1)+"1";
            dados.add(registro(conta,grupo,1,Perspectiva.REALIZADO,grupo.equals("RECEITA")?"100":"20"));
            dados.add(registro(conta,grupo,1,Perspectiva.ORCAMENTO,grupo.equals("RECEITA")?"90":"20"));
            dados.add(registro(conta,grupo,2,Perspectiva.PROJETADO,grupo.equals("RECEITA")?"200":"50"));
            dados.add(registro(conta,grupo,2,Perspectiva.ORCAMENTO,grupo.equals("RECEITA")?"150":"50"));
        }
        // CUSTO e OPEX precisam de IDs distintos.
        dre.salvarTodos(dados);
    }
    private DreRegistro registro(String conta,String grupo,int mes,Perspectiva perspectiva,String valor){
        return new DreRegistro(grupo+conta,null,grupo,grupo,2026,mes,perspectiva,new BigDecimal(valor),NaturezaDado.FICTICIO);
    }
    private BigDecimal valor(Cenario c,String grupo,int mes){
        return service.calcular(c.id()).linhas().stream().filter(l->l.grupo().equals(grupo)&&l.mes()==mes)
            .findFirst().orElseThrow().valorForecast();
    }
    @Test void aplicaRealizadoNosMesesFechadosEProjetadoNosAbertos() {
        var c=service.criar("Base",2026,1);
        assertEquals(0,valor(c,"RECEITA",1).compareTo(new BigDecimal("100")));
        assertEquals(0,valor(c,"RECEITA",2).compareTo(new BigDecimal("200")));
        var atualizado=service.alterarVariacao(c.id(),"RECEITA",new BigDecimal("10"));
        assertEquals(0,valor(atualizado,"RECEITA",1).compareTo(new BigDecimal("100")));
        assertEquals(0,valor(atualizado,"RECEITA",2).compareTo(new BigDecimal("220")));
        var mes1=service.calcular(atualizado.id()).meses().get(0);
        assertEquals(0,mes1.ebitdaDemonstrativo().compareTo(new BigDecimal("60")));
    }
    @Test void naoCompartilhaPremissasEntreCenarios() {
        var a=service.criar("A",2026,1);
        var b=service.criar("B",2026,1);
        service.alterarVariacao(a.id(),"RECEITA",new BigDecimal("10"));
        assertEquals(0,valor(service.buscar(a.id()),"RECEITA",2).compareTo(new BigDecimal("220")));
        assertEquals(0,valor(service.buscar(b.id()),"RECEITA",2).compareTo(new BigDecimal("200")));
        service.definirAjuste(a.id(),"RECEITAR1",2,new BigDecimal("250"));
        assertEquals(0,valor(service.buscar(a.id()),"RECEITA",2).compareTo(new BigDecimal("250")));
        assertEquals(0,valor(service.buscar(b.id()),"RECEITA",2).compareTo(new BigDecimal("200")));
    }
    @Test void impedeAjustesNoFechadoEEmContaSemProjecao() {
        var c=service.criar("Base",2026,1);
        assertThrows(RegraNegocioException.class,
            ()->service.definirAjuste(c.id(),"RECEITAR1",1,BigDecimal.TEN));
        assertThrows(RegraNegocioException.class,
            ()->service.definirAjuste(c.id(),"INEXISTENTE",2,BigDecimal.TEN));
    }
    @Test void naoModificaBaseOriginalOuCenarioJaCriadoAposImportacao() {
        var antes=service.criar("Antes",2026,1);
        dre.salvarTodos(List.of(registro("N1","RECEITA",3,Perspectiva.PROJETADO,"999")));
        var depois=service.criar("Depois",2026,1);
        assertEquals(12,antes.base().size());
        assertEquals(13,depois.base().size());
    }
    @Test void mantemPendenciasSeDadosFaltam() {
        var c=service.criar("Base",2026,1);
        var mes3=service.calcular(c.id()).meses().get(2);
        assertEquals("INCOMPLETO",mes3.status());
        assertNull(mes3.ebitdaDemonstrativo());
        assertFalse(mes3.pendencias().isEmpty());
    }
}
