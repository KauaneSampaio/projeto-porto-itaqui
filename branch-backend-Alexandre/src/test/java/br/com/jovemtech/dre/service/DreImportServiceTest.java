package br.com.jovemtech.dre.service;
import br.com.jovemtech.dre.repository.InMemoryDreRepository;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
class DreImportServiceTest {
 @Test void importaArquivoValido()throws Exception {
  var service=new DreImportService(new InMemoryDreRepository());
  var csv="conta_id;conta_pai_id;descricao;grupo;ano;mes;perspectiva;valor;natureza\nA;;Receita;RECEITA;2026;1;ORCAMENTO;10.50;FICTICIO\n";
  assertEquals(1,service.importar(new MockMultipartFile("arquivo","teste.csv","text/csv",csv.getBytes(StandardCharsets.UTF_8))));
  assertEquals(1,service.listar().size());
 }
 @Test void rejeitaLoteInteiroSeHouverLinhaInvalida()throws Exception {
  var service=new DreImportService(new InMemoryDreRepository());
  var csv="conta_id;conta_pai_id;descricao;grupo;ano;mes;perspectiva;valor;natureza\nA;;Receita;RECEITA;2026;1;ORCAMENTO;10;FICTICIO\nB;;Custo;CUSTO;2026;13;REALIZADO;5;SIMULADO\n";
  assertThrows(ImportacaoException.class,()->service.importar(new MockMultipartFile("arquivo","teste.csv","text/csv",csv.getBytes(StandardCharsets.UTF_8))));
  assertTrue(service.listar().isEmpty());
 }
}
