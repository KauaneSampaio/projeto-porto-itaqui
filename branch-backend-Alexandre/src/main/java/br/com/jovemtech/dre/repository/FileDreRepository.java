package br.com.jovemtech.dre.repository;

import br.com.jovemtech.dre.model.DreRegistro;
import br.com.jovemtech.dre.storage.ArquivoJson;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

/** Persistência local atômica por arquivo, adequada para demonstração/MVP sem banco externo. */
@Repository
public class FileDreRepository implements DreRepository {
    private final ArquivoJson arquivo;
    private final List<DreRegistro> dados;

    public FileDreRepository(ObjectMapper mapper, @Value("${jovemtech.storage.dre:data/dre-registros.json}") String caminho) {
        this.arquivo = new ArquivoJson(mapper, Path.of(caminho));
        this.dados = new ArrayList<>(arquivo.ler(new TypeReference<List<DreRegistro>>() {}, List.of()));
        RegistroValidator.validarAcrescimo(List.of(),this.dados);
    }

    @Override public synchronized void salvarTodos(List<DreRegistro> novos) {
        RegistroValidator.validarAcrescimo(dados, novos);
        List<DreRegistro> atualizados=new ArrayList<>(dados);
        atualizados.addAll(novos);
        arquivo.salvar(atualizados);
        dados.clear();
        dados.addAll(atualizados);
    }

    @Override public synchronized void substituirTodos(List<DreRegistro> registros) {
        RegistroValidator.validarAcrescimo(List.of(), registros);
        arquivo.salvar(List.copyOf(registros));
        dados.clear();dados.addAll(registros);
    }

    @Override public synchronized List<DreRegistro> listar() {return List.copyOf(dados);}
}
