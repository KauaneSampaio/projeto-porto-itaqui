package br.com.jovemtech.dre.api;

import br.com.jovemtech.dre.service.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ImportacaoException.class)
    public ResponseEntity<Map<String,Object>> invalido(ImportacaoException ex){
        return ResponseEntity.badRequest().body(Map.of("erro",ex.getMessage(),"detalhes",ex.getErros()));
    }
    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<Map<String,String>> regra(RegraNegocioException ex){
        return ResponseEntity.badRequest().body(Map.of("erro",ex.getMessage()));
    }
    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<Map<String,String>> naoEncontrado(RecursoNaoEncontradoException ex){
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro",ex.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,String>> validacao(MethodArgumentNotValidException ex){
        String detalhe=ex.getBindingResult().getFieldErrors().stream()
            .map(e->e.getField()+": "+e.getDefaultMessage()).findFirst().orElse("Dados inválidos");
        return ResponseEntity.badRequest().body(Map.of("erro",detalhe));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String,String>> json(HttpMessageNotReadableException ex){
        return ResponseEntity.badRequest().body(Map.of("erro","JSON inválido ou campo com tipo incorreto"));
    }
}
