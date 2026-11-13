package br.com.jovemtech.dre.api;
import br.com.jovemtech.dre.model.DreRegistro;
import br.com.jovemtech.dre.service.DreImportService;
import java.io.IOException;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController @RequestMapping("/api/v1/dre") public class DreController {
 private final DreImportService service;
 public DreController(DreImportService service){this.service=service;}
 @PostMapping(value="/importacoes",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
 public Map<String,Object> importar(@RequestParam("arquivo") MultipartFile arquivo)throws IOException {return Map.of("importados",service.importar(arquivo),"status","sucesso");}
 @GetMapping("/registros")public List<DreRegistro> listar(){return service.listar();}
}
