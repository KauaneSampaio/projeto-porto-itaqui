package br.com.jovemtech.dre.repository;

import br.com.jovemtech.dre.model.Cenario;
import br.com.jovemtech.dre.storage.ArquivoJson;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
public class FileCenarioRepository implements CenarioRepository {
    private final ArquivoJson arquivo;
    private final Map<UUID,Cenario> cenarios=new LinkedHashMap<>();

    public FileCenarioRepository(ObjectMapper mapper,@Value("${jovemtech.storage.cenarios:data/cenarios.json}") String caminho) {
        arquivo=new ArquivoJson(mapper,Path.of(caminho));
        List<Cenario> lidos=arquivo.ler(new TypeReference<List<Cenario>>() {},List.of());
        for(Cenario c:lidos)cenarios.put(c.id(),c);
    }
    @Override public synchronized void salvar(Cenario c) {
        var atualizados=new LinkedHashMap<>(cenarios);
        atualizados.put(c.id(),c);
        arquivo.salvar(List.copyOf(atualizados.values()));
        cenarios.clear();cenarios.putAll(atualizados);
    }
    @Override public synchronized Optional<Cenario> buscar(UUID id){return Optional.ofNullable(cenarios.get(id));}
    @Override public synchronized List<Cenario> listar(){return List.copyOf(cenarios.values());}
}
