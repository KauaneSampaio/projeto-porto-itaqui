package br.com.jovemtech.dre.api;

import br.com.jovemtech.dre.forecast.ResultadoForecast;
import br.com.jovemtech.dre.model.Cenario;
import br.com.jovemtech.dre.model.AjusteCenario;
import br.com.jovemtech.dre.service.CenarioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.net.URI;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cenarios")
public class CenarioController {
    private final CenarioService service;
    public CenarioController(CenarioService service){this.service=service;}
    public record NovoCenario(@NotBlank String nome,@NotNull Integer ano,@NotNull Integer ultimoMesFechado) { }
    public record Variacao(@NotBlank String grupo,@NotNull BigDecimal variacaoPercentual) { }
    public record Ajuste(@NotBlank String contaId,@NotNull Integer mes,@NotNull BigDecimal valorFinal) { }
    /** DTO sem a cópia interna inteira dos dados de origem. */
    public record CenarioVisao(UUID id,String nome,int ano,int ultimoMesFechado,
        Map<String,BigDecimal> variacaoPorGrupo,List<AjusteCenario> ajustes,int quantidadeRegistrosBase) {
        public static CenarioVisao de(Cenario c){return new CenarioVisao(c.id(),c.nome(),c.ano(),c.ultimoMesFechado(),
            c.variacaoPorGrupo(),c.ajustes(),c.base().size());}
    }

    @PostMapping
    public ResponseEntity<CenarioVisao> criar(@Valid @RequestBody NovoCenario pedido) {
        Cenario c=service.criar(pedido.nome(),pedido.ano(),pedido.ultimoMesFechado());
        return ResponseEntity.created(URI.create("/api/v1/cenarios/"+c.id())).body(CenarioVisao.de(c));
    }
    @GetMapping public List<CenarioVisao> listar(){return service.listar().stream().map(CenarioVisao::de).toList();}
    @GetMapping("/{id}")public CenarioVisao buscar(@PathVariable UUID id){return CenarioVisao.de(service.buscar(id));}
    @PutMapping("/{id}/premissas")
    public CenarioVisao premissa(@PathVariable UUID id,@Valid @RequestBody Variacao pedido){
        return CenarioVisao.de(service.alterarVariacao(id,pedido.grupo(),pedido.variacaoPercentual()));
    }
    @PutMapping("/{id}/ajustes")
    public CenarioVisao ajuste(@PathVariable UUID id,@Valid @RequestBody Ajuste pedido){
        return CenarioVisao.de(service.definirAjuste(id,pedido.contaId(),pedido.mes(),pedido.valorFinal()));
    }
    @GetMapping("/{id}/forecast")public ResultadoForecast forecast(@PathVariable UUID id){return service.calcular(id);}
}
