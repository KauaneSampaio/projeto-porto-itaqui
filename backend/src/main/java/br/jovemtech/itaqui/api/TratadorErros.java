package br.jovemtech.itaqui.api;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Todas as respostas de erro têm o formato {@code {erro, mensagem, detalhes}}. */
@RestControllerAdvice
public class TratadorErros {

    public record Erro(String erro, String mensagem, List<?> detalhes) {
    }

    private static final Logger LOG = LoggerFactory.getLogger(TratadorErros.class);

    private static ResponseEntity<Erro> resposta(HttpStatus status, String codigo, String mensagem, List<?> detalhes) {
        return ResponseEntity.status(status).body(new Erro(codigo, mensagem, detalhes == null ? List.of() : detalhes));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Erro> api(ApiException e) {
        return resposta(e.status(), e.codigo(), e.getMessage(), e.detalhes());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Erro> validacao(MethodArgumentNotValidException e) {
        List<String> detalhes = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).toList();
        return resposta(HttpStatus.BAD_REQUEST, "REQUISICAO_INVALIDA", "Campos inválidos na requisição", detalhes);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<Erro> validacaoMetodo(HandlerMethodValidationException e) {
        List<String> detalhes = e.getAllErrors().stream().map(m -> String.valueOf(m.getDefaultMessage())).toList();
        return resposta(HttpStatus.BAD_REQUEST, "REQUISICAO_INVALIDA", "Parâmetros inválidos na requisição",
                detalhes);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Erro> jsonInvalido(HttpMessageNotReadableException e) {
        Throwable causa = e.getMostSpecificCause();
        String msg = causa instanceof IllegalArgumentException ? causa.getMessage() : "Corpo JSON ausente ou inválido";
        return resposta(HttpStatus.BAD_REQUEST, "REQUISICAO_INVALIDA", msg, null);
    }

    @ExceptionHandler({ MissingServletRequestParameterException.class, MissingServletRequestPartException.class })
    ResponseEntity<Erro> faltando(Exception e) {
        return resposta(HttpStatus.BAD_REQUEST, "REQUISICAO_INVALIDA", e.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Erro> tipo(MethodArgumentTypeMismatchException e) {
        return resposta(HttpStatus.BAD_REQUEST, "REQUISICAO_INVALIDA",
                "Valor inválido para '" + e.getName() + "': " + e.getValue(), null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Erro> argumento(IllegalArgumentException e) {
        return resposta(HttpStatus.BAD_REQUEST, "REQUISICAO_INVALIDA", e.getMessage(), null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Erro> naoEncontrado(NoResourceFoundException e) {
        return resposta(HttpStatus.NOT_FOUND, "NAO_ENCONTRADO", "Recurso não encontrado: /" + e.getResourcePath(),
                null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Erro> metodo(HttpRequestMethodNotSupportedException e) {
        return resposta(HttpStatus.METHOD_NOT_ALLOWED, "METODO_NAO_PERMITIDO", e.getMessage(), null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Erro> tamanho(MaxUploadSizeExceededException e) {
        return resposta(HttpStatus.PAYLOAD_TOO_LARGE, "ARQUIVO_GRANDE", "Arquivo maior que o permitido (10 MB)", null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Erro> inesperado(Exception e) {
        LOG.error("Erro inesperado", e);
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "ERRO_INTERNO", "Erro inesperado no servidor", null);
    }
}
