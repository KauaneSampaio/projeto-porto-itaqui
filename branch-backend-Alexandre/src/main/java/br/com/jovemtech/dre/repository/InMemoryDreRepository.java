package br.com.jovemtech.dre.repository;

import br.com.jovemtech.dre.model.DreRegistro;
import java.util.*;

/** Repositório usado em testes; a aplicação utiliza o repositório de arquivo. */
public class InMemoryDreRepository implements DreRepository {
    private final List<DreRegistro> dados = new ArrayList<>();

    @Override public synchronized void salvarTodos(List<DreRegistro> registros) {
        RegistroValidator.validarAcrescimo(dados, registros);
        dados.addAll(registros);
    }

    @Override public synchronized void substituirTodos(List<DreRegistro> registros) {
        RegistroValidator.validarAcrescimo(List.of(), registros);
        dados.clear();dados.addAll(registros);
    }
    @Override public synchronized List<DreRegistro> listar() { return List.copyOf(dados); }
}
