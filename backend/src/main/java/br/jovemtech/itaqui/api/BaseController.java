package br.jovemtech.itaqui.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.jovemtech.itaqui.api.dto.MetaResposta;
import br.jovemtech.itaqui.dados.ArquivosBase;
import br.jovemtech.itaqui.dados.RelatorioImportacao;
import br.jovemtech.itaqui.servico.ServicoBase;

/** Importação (RF002, CT-07), exportação e restauração da base. */
@RestController
@RequestMapping("/api")
public class BaseController {

    private final ServicoBase servico;

    public BaseController(ServicoBase servico) {
        this.servico = servico;
    }

    /**
     * Recebe os 5 CSVs (partes multipart {@code config}, {@code contas}, {@code valores},
     * {@code premissas}, {@code vinculos}). Tudo ou nada: com qualquer erro devolve 422 e a
     * base atual fica intacta. {@code simular=true} só valida.
     */
    @PostMapping(path = "/importacao", consumes = "multipart/form-data")
    public ResponseEntity<RelatorioImportacao> importar(
            @RequestPart(name = "config", required = false) MultipartFile config,
            @RequestPart(name = "contas", required = false) MultipartFile contas,
            @RequestPart(name = "valores", required = false) MultipartFile valores,
            @RequestPart(name = "premissas", required = false) MultipartFile premissas,
            @RequestPart(name = "vinculos", required = false) MultipartFile vinculos,
            @RequestParam(defaultValue = "false") boolean simular) throws IOException {
        Map<String, String> arquivos = new LinkedHashMap<>();
        arquivos.put(ArquivosBase.CONFIG, texto(config));
        arquivos.put(ArquivosBase.CONTAS, texto(contas));
        arquivos.put(ArquivosBase.VALORES, texto(valores));
        arquivos.put(ArquivosBase.PREMISSAS, texto(premissas));
        arquivos.put(ArquivosBase.VINCULOS, texto(vinculos));
        RelatorioImportacao rel = servico.importar(arquivos, simular);
        return ResponseEntity.status(rel.valido() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY).body(rel);
    }

    private static String texto(MultipartFile arquivo) throws IOException {
        return arquivo == null ? "" : new String(arquivo.getBytes(), StandardCharsets.UTF_8);
    }

    /** Baixa um dos arquivos da base atual (serve de modelo para a equipe de Dados). */
    @GetMapping("/base/arquivos/{nome}")
    public ResponseEntity<byte[]> arquivo(@PathVariable String nome) {
        return Csv.anexo(nome, servico.exportarArquivo(nome));
    }

    /** Volta para a base fictícia do projeto. Os cenários são mantidos. */
    @PostMapping("/base/restaurar")
    public MetaResposta restaurar() {
        return servico.restaurar();
    }
}
