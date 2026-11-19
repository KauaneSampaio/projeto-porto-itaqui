package br.jovemtech.itaqui.api;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Resposta CSV com BOM UTF-8 (o Excel abre os acentos corretamente). */
final class Csv {

    private Csv() {
    }

    static ResponseEntity<byte[]> anexo(String nomeArquivo, String conteudo) {
        byte[] corpo = ("﻿" + conteudo).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nomeArquivo).build().toString())
                .body(corpo);
    }
}
