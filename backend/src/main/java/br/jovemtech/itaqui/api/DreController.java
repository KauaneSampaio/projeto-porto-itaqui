package br.jovemtech.itaqui.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.jovemtech.itaqui.api.dto.NoConta;
import br.jovemtech.itaqui.api.dto.NovaContaRequest;
import br.jovemtech.itaqui.dominio.Conta;
import br.jovemtech.itaqui.servico.ServicoBase;
import jakarta.validation.Valid;

/** Estrutura hierárquica da DRE (RF001). */
@RestController
@RequestMapping("/api/dre")
public class DreController {

    private final ServicoBase servico;

    public DreController(ServicoBase servico) {
        this.servico = servico;
    }

    @GetMapping("/estrutura")
    public List<NoConta> estrutura() {
        return servico.estrutura();
    }

    @PostMapping("/contas")
    @ResponseStatus(HttpStatus.CREATED)
    public Conta criarConta(@Valid @RequestBody NovaContaRequest req) {
        return servico.criarConta(req);
    }

    @DeleteMapping("/contas/{codigo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerConta(@PathVariable String codigo) {
        servico.removerConta(codigo);
    }
}
