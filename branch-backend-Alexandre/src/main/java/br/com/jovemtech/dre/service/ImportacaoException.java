package br.com.jovemtech.dre.service;
import java.util.List;
public class ImportacaoException extends RuntimeException {
 private final List<String> erros;
 public ImportacaoException(List<String> erros){super("Arquivo rejeitado: registros inválidos");this.erros=List.copyOf(erros);}
 public List<String> getErros(){return erros;}
}
