package br.com.jovemtech.dre.repository;

import br.com.jovemtech.dre.model.DreRegistro;
import br.com.jovemtech.dre.service.ImportacaoException;
import java.util.*;

/** Evita duplicidades e inconsistências de classificação. */
public final class RegistroValidator {
    private RegistroValidator() { }
    private record Chave(String contaId, int ano, int mes, String perspectiva) { }

    public static void validarAcrescimo(List<DreRegistro> existentes, List<DreRegistro> novos) {
        var vistos = new HashSet<Chave>();
        var metadata = new HashMap<String, DreRegistro>();
        for (DreRegistro r : existentes) {
            vistos.add(new Chave(r.contaId(),r.ano(),r.mes(),r.perspectiva().name()));
            metadata.put(r.contaId()+"@"+r.ano(),r);
        }
        var erros = new ArrayList<String>();
        for (DreRegistro r : novos) {
            var chave = new Chave(r.contaId(),r.ano(),r.mes(),r.perspectiva().name());
            if (!vistos.add(chave)) erros.add("Registro duplicado: conta="+r.contaId()+", ano="+r.ano()+", mes="+r.mes()+", perspectiva="+r.perspectiva());
            String metaChave = r.contaId()+"@"+r.ano();
            DreRegistro anterior = metadata.putIfAbsent(metaChave,r);
            if (anterior != null && (!anterior.grupo().equals(r.grupo()) ||
                !anterior.descricao().equals(r.descricao()) ||
                !Objects.equals(anterior.contaPaiId(),r.contaPaiId()))) {
                erros.add("Metadados inconsistentes da conta "+r.contaId()+" no ano "+r.ano());
            }
        }
        if (!erros.isEmpty()) throw new ImportacaoException(erros);
    }
}
