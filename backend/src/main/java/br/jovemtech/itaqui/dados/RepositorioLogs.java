package br.jovemtech.itaqui.dados;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.jovemtech.itaqui.config.PropriedadesApp;
import br.jovemtech.itaqui.dominio.RegistroLog;

/**
 * Armazena logs de auditoria em memória e persiste em JSON de forma atômica.
 */
@Repository
public class RepositorioLogs {

    private static final Logger log = LoggerFactory.getLogger(RepositorioLogs.class);
    private static final TypeReference<List<RegistroLog>> TIPO_LISTA = new TypeReference<>() {
    };

    private final Path caminhoArquivo;
    private final ObjectMapper mapper;
    private final List<RegistroLog> registros = new ArrayList<>();

    public RepositorioLogs(PropriedadesApp props, ObjectMapper mapper) {
        this.caminhoArquivo = props.dados().diretorio().resolve("logs-auditoria.json");
        this.mapper = mapper;
        carregarDoDisco();
    }

    private synchronized void carregarDoDisco() {
        if (!Files.exists(caminhoArquivo)) {
            return;
        }
        try {
            List<RegistroLog> salvos = mapper.readValue(caminhoArquivo.toFile(), TIPO_LISTA);
            registros.clear();
            if (salvos != null) {
                registros.addAll(salvos);
            }
        } catch (IOException e) {
            log.warn("Falha ao ler {}: {}", caminhoArquivo, e.getMessage());
        }
    }

    public synchronized void salvar(String usuario, String operacao, String entidade, String identificador, String detalhes) {
        String id = "log-" + UUID.randomUUID().toString().substring(0, 8);
        RegistroLog reg = new RegistroLog(id, usuario, operacao, entidade, identificador, detalhes);
        registros.add(0, reg);
        if (registros.size() > 500) {
            registros.remove(registros.size() - 1);
        }
        gravarEmDisco();
    }

    public synchronized List<RegistroLog> listar() {
        return Collections.unmodifiableList(new ArrayList<>(registros));
    }

    private void gravarEmDisco() {
        try {
            Files.createDirectories(caminhoArquivo.getParent());
            Path tmp = caminhoArquivo.resolveSibling(caminhoArquivo.getFileName() + ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), registros);
            Files.move(tmp, caminhoArquivo, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("Falha ao gravar logs em {}: {}", caminhoArquivo, e.getMessage());
        }
    }
}
