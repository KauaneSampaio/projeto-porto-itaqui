package br.jovemtech.itaqui.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.jovemtech.itaqui.api.dto.NovaPremissaRequest;
import br.jovemtech.itaqui.api.dto.PremissaResposta;
import br.jovemtech.itaqui.api.dto.VinculoRequest;
import br.jovemtech.itaqui.api.dto.VinculoResposta;
import br.jovemtech.itaqui.servico.ServicoBase;
import jakarta.validation.Valid;

/** Premissas/índices do orçamento original (RF003) e vínculos conta → premissa (RF004). */
@RestController
@RequestMapping("/api")
public class PremissaController {

    private final ServicoBase servico;

    public PremissaController(ServicoBase servico) {
        this.servico = servico;
    }

    @GetMapping("/premissas")
    public List<PremissaResposta> premissas() {
        return servico.premissas();
    }

    @GetMapping("/premissas/{codigo}")
    public PremissaResposta premissa(@PathVariable String codigo) {
        return servico.premissa(codigo);
    }

    @PostMapping("/premissas")
    @ResponseStatus(HttpStatus.CREATED)
    public PremissaResposta criarPremissa(@Valid @RequestBody NovaPremissaRequest req) {
        return servico.criarPremissa(req);
    }

    @GetMapping("/vinculos")
    public List<VinculoResposta> vinculos() {
        return servico.vinculos();
    }

    @PostMapping("/vinculos")
    @ResponseStatus(HttpStatus.CREATED)
    public VinculoResposta criarVinculo(@Valid @RequestBody VinculoRequest req) {
        return servico.criarVinculo(req);
    }

    @DeleteMapping("/vinculos")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerVinculo(@RequestParam String conta, @RequestParam String premissa) {
        servico.removerVinculo(conta, premissa);
    }
}
