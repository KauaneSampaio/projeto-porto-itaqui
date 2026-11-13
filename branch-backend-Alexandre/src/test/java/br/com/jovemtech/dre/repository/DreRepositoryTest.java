package br.com.jovemtech.dre.repository;
import br.com.jovemtech.dre.model.*;
import br.com.jovemtech.dre.service.ImportacaoException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DreRepositoryTest {
    @Test void loteDuplicadoNaoAlteraRegistrosExistentes() {
        var repo=new InMemoryDreRepository();
        var r=new DreRegistro("R1",null,"Receita","RECEITA",2026,1,Perspectiva.ORCAMENTO,
            BigDecimal.TEN,NaturezaDado.FICTICIO);
        repo.salvarTodos(List.of(r));
        assertThrows(ImportacaoException.class,()->repo.salvarTodos(List.of(r)));
        assertEquals(1,repo.listar().size());
    }
    @Test void mesmaContaPodeTerRealizadoEOrcamentoNoMesmoMes() {
        var repo=new InMemoryDreRepository();
        var r=new DreRegistro("R1",null,"Receita","RECEITA",2026,1,Perspectiva.ORCAMENTO,
            BigDecimal.TEN,NaturezaDado.FICTICIO);
        repo.salvarTodos(List.of(r,new DreRegistro("R1",null,"Receita","RECEITA",2026,1,
            Perspectiva.REALIZADO,BigDecimal.ONE,NaturezaDado.FICTICIO)));
        assertEquals(2,repo.listar().size());
    }
}
