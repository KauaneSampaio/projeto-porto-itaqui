package br.com.jovemtech.dre.repository;
import br.com.jovemtech.dre.model.Cenario;
import java.util.*;

public class InMemoryCenarioRepository implements CenarioRepository {
    private final Map<UUID,Cenario> cenarios=new LinkedHashMap<>();
    @Override public synchronized void salvar(Cenario c) {cenarios.put(c.id(),c);}
    @Override public synchronized Optional<Cenario> buscar(UUID id){return Optional.ofNullable(cenarios.get(id));}
    @Override public synchronized List<Cenario> listar(){return List.copyOf(cenarios.values());}
}
