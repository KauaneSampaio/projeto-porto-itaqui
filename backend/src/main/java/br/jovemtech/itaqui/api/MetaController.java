package br.jovemtech.itaqui.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.jovemtech.itaqui.api.dto.MetaResposta;
import br.jovemtech.itaqui.servico.ServicoBase;

@RestController
@RequestMapping("/api")
public class MetaController {

    private final ServicoBase servico;

    public MetaController(ServicoBase servico) {
        this.servico = servico;
    }

    /** Data de corte, anos disponíveis, origem dos dados (RF012) e regras em uso. */
    @GetMapping("/meta")
    public MetaResposta meta() {
        return servico.meta();
    }
}
