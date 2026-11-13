package br.com.jovemtech.dre.repository;
import br.com.jovemtech.dre.model.DreRegistro;
import java.util.List;
public interface DreRepository {
    void salvarTodos(List<DreRegistro> registros);
    void substituirTodos(List<DreRegistro> registros);
    List<DreRegistro> listar();
}
