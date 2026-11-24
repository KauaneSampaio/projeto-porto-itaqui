package br.jovemtech.itaqui.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.jovemtech.itaqui.api.dto.AjustePremissaRequest;
import br.jovemtech.itaqui.api.dto.CenarioResposta;
import br.jovemtech.itaqui.api.dto.ComparativoResposta;
import br.jovemtech.itaqui.api.dto.NovoCenarioRequest;
import br.jovemtech.itaqui.api.dto.PremissaCenarioResposta;
import br.jovemtech.itaqui.api.dto.RenomearCenarioRequest;
import br.jovemtech.itaqui.calculo.ResultadoDre;
import br.jovemtech.itaqui.dominio.Cenario;
import br.jovemtech.itaqui.servico.ServicoCenario;
import jakarta.validation.Valid;

/**
 * Cenários (RF009) e os cálculos de DRE, EBITDA e forecast sobre eles (RF005–RF008).
 * O cenário {@code original} é somente leitura.
 */
@RestController
@RequestMapping("/api/cenarios")
public class CenarioController {

    private final ServicoCenario servico;

    public CenarioController(ServicoCenario servico) {
        this.servico = servico;
    }

    @GetMapping
    public List<CenarioResposta> listar() {
        return servico.listar();
    }

    @GetMapping("/{id}")
    public CenarioResposta detalhe(@PathVariable String id) {
        return servico.detalhe(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CenarioResposta criar(@Valid @RequestBody NovoCenarioRequest req) {
        return servico.criar(req.nome(), req.copiarDe());
    }

    @PatchMapping("/{id}")
    public CenarioResposta renomear(@PathVariable String id, @Valid @RequestBody RenomearCenarioRequest req) {
        return servico.renomear(id, req.nome());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable String id) {
        servico.remover(id);
    }

    @GetMapping("/{id}/premissas")
    public List<PremissaCenarioResposta> premissas(@PathVariable String id) {
        return servico.premissasDoCenario(id);
    }

    @PutMapping("/{id}/premissas/{codigo}")
    public CenarioResposta ajustarPremissa(@PathVariable String id, @PathVariable String codigo,
            @Valid @RequestBody AjustePremissaRequest req) {
        return servico.ajustarPremissa(id, codigo, req.ajustes());
    }

    @DeleteMapping("/{id}/premissas/{codigo}")
    public CenarioResposta removerAjustes(@PathVariable String id, @PathVariable String codigo) {
        return servico.removerAjustes(id, codigo);
    }

    @GetMapping("/{id}/dre")
    public ResultadoDre dre(@PathVariable String id, @RequestParam int ano) {
        return servico.dre(id, ano);
    }

    @GetMapping("/{id}/comparativo")
    public ComparativoResposta comparativo(@PathVariable String id, @RequestParam int ano,
            @RequestParam(defaultValue = Cenario.ID_ORIGINAL) String base) {
        return servico.comparativo(id, ano, base);
    }
}
