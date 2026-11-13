package br.com.jovemtech.dre.repository;
import br.com.jovemtech.dre.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FileRepositoryTest {
    @TempDir Path temp;
    @Test void persisteRegistrosEReabreComSucesso() {
        String caminho=temp.resolve("dre.json").toString();
        var repo=new FileDreRepository(new ObjectMapper(),caminho);
        repo.salvarTodos(List.of(new DreRegistro("REC",null,"Receita","RECEITA",2026,1,
            Perspectiva.ORCAMENTO,new BigDecimal("50.50"),NaturezaDado.FICTICIO)));
        var reaberto=new FileDreRepository(new ObjectMapper(),caminho);
        assertEquals(1,reaberto.listar().size());
        assertEquals(0,reaberto.listar().get(0).valor().compareTo(new BigDecimal("50.50")));
    }
    @Test void persisteCenarioEReabreSemPerderPremissa() {
        String caminho=temp.resolve("cenarios.json").toString();
        var repo=new FileCenarioRepository(new ObjectMapper(),caminho);
        var c=new Cenario(UUID.randomUUID(),"Teste",2026,2,List.of(),
            Map.of("RECEITA",new BigDecimal("7.5")),List.of());
        repo.salvar(c);
        var reaberto=new FileCenarioRepository(new ObjectMapper(),caminho);
        assertEquals(c.id(),reaberto.buscar(c.id()).orElseThrow().id());
        assertEquals(0,reaberto.buscar(c.id()).orElseThrow().variacaoPorGrupo()
            .get("RECEITA").compareTo(new BigDecimal("7.5")));
    }
}
