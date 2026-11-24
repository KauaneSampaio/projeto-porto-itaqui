package br.jovemtech.itaqui.api;

import java.util.List;

import org.springframework.http.HttpStatus;

/** Erro de negócio com status HTTP e código estável para o front-end tratar. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;
    private final transient List<?> detalhes;

    public ApiException(HttpStatus status, String codigo, String mensagem, List<?> detalhes) {
        super(mensagem);
        this.status = status;
        this.codigo = codigo;
        this.detalhes = detalhes == null ? List.of() : detalhes;
    }

    public static ApiException naoEncontrado(String mensagem) {
        return new ApiException(HttpStatus.NOT_FOUND, "NAO_ENCONTRADO", mensagem, null);
    }

    public static ApiException conflito(String codigo, String mensagem) {
        return new ApiException(HttpStatus.CONFLICT, codigo, mensagem, null);
    }

    public static ApiException regra(String codigo, String mensagem) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, codigo, mensagem, null);
    }

    public static ApiException regra(String codigo, String mensagem, List<?> detalhes) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, codigo, mensagem, detalhes);
    }

    public HttpStatus status() {
        return status;
    }

    public String codigo() {
        return codigo;
    }

    public List<?> detalhes() {
        return detalhes;
    }
}
