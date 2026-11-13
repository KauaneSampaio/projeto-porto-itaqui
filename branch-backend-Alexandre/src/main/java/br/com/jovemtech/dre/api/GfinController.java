package br.com.jovemtech.dre.api;

import br.com.jovemtech.dre.model.DreRegistro;
import br.com.jovemtech.dre.service.DreImportService;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** API do modelo de contas GFIN; arquivos de auditoria não são apagados nem mutados. */
@RestController
@RequestMapping("/api/v1/gfin")
public class GfinController {
    private final DreImportService dre;
    public GfinController(DreImportService dre) {this.dre=dre;}
    @PostMapping(value="/importacoes",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> importar(@RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam(value="substituirBase",required=false,defaultValue="false") boolean substituirBase) throws IOException {
        int total=dre.importar(arquivo,substituirBase);
        return Map.of("status","sucesso","importados",total,"substituiuBase",substituirBase,
            "aviso","Recrie os cenários após substituir a base; snapshots anteriores não são atualizados.");
    }
    @GetMapping("/contas") public List<Map<String,String>> contas() throws IOException {
        return lerTabela("/gfin/gfin-catalogo-contas.csv");
    }
    @GetMapping("/indices/2027") public List<Map<String,String>> indices() throws IOException {
        return lerTabela("/gfin/gfin-indices-2027.csv");
    }
    @GetMapping("/inconsistencias") public List<Map<String,String>> inconsistencias() throws IOException {
        return lerTabela("/gfin/gfin-inconsistencias.csv");
    }
    @GetMapping("/comparativo-anual") public List<Map<String,String>> comparativoAnual() throws IOException {
        return lerTabela("/gfin/gfin-comparativo-anual-2024-2027.csv");
    }
    @GetMapping("/status") public Map<String,Object> status() {
        List<DreRegistro> regs=dre.listar();
        long gfin=regs.stream().filter(r->r.natureza().name().startsWith("GFIN_")).count();
        var mesConta=new TreeMap<Integer,Set<String>>();
        regs.stream().filter(r->r.ano()==2026&&r.perspectiva()==br.com.jovemtech.dre.model.Perspectiva.REALIZADO
            &&r.natureza().name().startsWith("GFIN_"))
            .forEach(r->mesConta.computeIfAbsent(r.mes(),x->new HashSet<>()).add(r.contaId()));
        int completo=mesConta.entrySet().stream().filter(e->e.getValue().size()==47)
            .mapToInt(Map.Entry::getKey).max().orElse(0);
        return Map.of("registros",regs.size(),"registrosGfin",gfin,
            "ultimoMesCom47ContasRealizadasEm2026",completo,"validacaoNecessaria",true,
            "aviso","Fechamento identificado pela completude de 47 contas; confirmar o mês com a GFIN. Não assumir unidade monetária.");
    }
    private static List<Map<String,String>> lerTabela(String recurso) throws IOException {
        try(InputStream in=GfinController.class.getResourceAsStream(recurso)) {
            if(in==null)throw new FileNotFoundException("Recurso de catálogo indisponível: "+recurso);
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))) {
                String first=reader.readLine();if(first==null)return List.of();
                String[] head=first.replace("\uFEFF", "").split(";",-1);
                List<Map<String,String>> dados=new ArrayList<>();String line;
                while((line=reader.readLine())!=null) {
                    if(line.isBlank())continue;
                    String[] cols=line.split(";",-1);
                    if(cols.length!=head.length)throw new IOException("Catálogo com linha inconsistente em "+recurso);
                    Map<String,String> item=new LinkedHashMap<>();
                    for(int i=0;i<head.length;i++)item.put(head[i],cols[i]);
                    dados.add(item);
                }
                return List.copyOf(dados);
            }
        }
    }
}
