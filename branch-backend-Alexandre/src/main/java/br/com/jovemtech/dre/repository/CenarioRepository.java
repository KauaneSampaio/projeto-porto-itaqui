package br.com.jovemtech.dre.repository;

import br.com.jovemtech.dre.model.Cenario;
import java.util.*;
public interface CenarioRepository {
    void salvar(Cenario cenario);
    Optional<Cenario> buscar(UUID id);
    List<Cenario> listar();
}
