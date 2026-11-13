package br.com.jovemtech.dre.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;

/** Utilitário de escrita por arquivo temporário e substituição, sem depender de BD. */
public final class ArquivoJson {
    private final ObjectMapper mapper;
    private final Path caminho;
    public ArquivoJson(ObjectMapper mapper,Path caminho) {
        this.mapper=mapper;
        this.caminho=caminho.toAbsolutePath().normalize();
    }
    public <T> T ler(TypeReference<T> type,T vazio) {
        if(!Files.exists(caminho)) return vazio;
        try { return mapper.readValue(caminho.toFile(),type); }
        catch(IOException ex) {throw new IllegalStateException("Não foi possível ler "+caminho+": "+ex.getMessage(),ex);}
    }
    public void salvar(Object conteudo) {
        Path temporario=null;
        try {
            Files.createDirectories(caminho.getParent());
            temporario=Files.createTempFile(caminho.getParent(),"jovemtech-",".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporario.toFile(),conteudo);
            try { Files.move(temporario,caminho,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch(AtomicMoveNotSupportedException ex) {Files.move(temporario,caminho,StandardCopyOption.REPLACE_EXISTING);}
        } catch(IOException ex) {throw new IllegalStateException("Não foi possível gravar "+caminho+": "+ex.getMessage(),ex);}
        finally {if(temporario!=null)try{Files.deleteIfExists(temporario);}catch(IOException ignored){}}
    }
}
