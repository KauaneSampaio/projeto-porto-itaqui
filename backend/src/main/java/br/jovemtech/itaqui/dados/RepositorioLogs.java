package br.jovemtech.itaqui.dados;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.jovemtech.itaqui.dominio.RegistroLog;

/**
 * Armazena logs de auditoria em memória e persiste via {@link ArmazenamentoEstado}
 * (disco local ou PostgreSQL).
 */
@Repository
public class RepositorioLogs {

    private static final String CHAVE = "logs-auditoria.json";
    private static final Logger log = LoggerFactory.getLogger(RepositorioLogs.class);
    private static final TypeReference<List<RegistroLog>> TIPO_LISTA = new TypeReference<>() {
    };

    private final ArmazenamentoEstado armazenamento;
    private final ObjectMapper mapper;
    private final List<RegistroLog> registros = new ArrayList<>();

    public RepositorioLogs(ArmazenamentoEstado armazenamento, ObjectMapper mapper) {
        this.armazenamento = armazenamento;
        this.mapper = mapper;
        carregar();
    }

    private synchronized void carregar() {
        Optional<String> salvo = armazenamento.ler(CHAVE);
        if (salvo.isEmpty() || salvo.get().isBlank()) {
            return;
        }
        try {
            List<RegistroLog> lidos = mapper.readValue(salvo.get(), TIPO_LISTA);
            registros.clear();
            if (lidos != null) {
                registros.addAll(lidos);
            }
            log.info("{} registro(s) de auditoria carregado(s) de {}", registros.size(), armazenamento.descricaoDestino(CHAVE));
        } catch (IOException e) {
            log.warn("Falha ao ler {}: {}", armazenamento.descricaoDestino(CHAVE), e.getMessage());
        }
    }

    public synchronized void salvar(String usuario, String operacao, String entidade, String identificador, String detalhes) {
        String id = "log-" + UUID.randomUUID().toString().substring(0, 8);
        RegistroLog reg = new RegistroLog(id, usuario, operacao, entidade, identificador, detalhes);
        registros.add(0, reg);
        if (registros.size() > 500) {
            registros.remove(registros.size() - 1);
        }
        gravar();
    }

    public synchronized List<RegistroLog> listar() {
        return Collections.unmodifiableList(new ArrayList<>(registros));
    }

    private void gravar() {
        try {
            String conteudo = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(registros);
            armazenamento.gravar(CHAVE, conteudo, false);
        } catch (IOException | RuntimeException e) {
            log.error("Falha ao gravar logs em {}: {}", armazenamento.descricaoDestino(CHAVE), e.getMessage());
        }
    }
}
