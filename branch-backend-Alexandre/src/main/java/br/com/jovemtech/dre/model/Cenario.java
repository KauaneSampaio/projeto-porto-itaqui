package br.com.jovemtech.dre.model;

import java.math.BigDecimal;
import java.util.*;

/** Cenário isolado: snapshot base capturado no momento da criação. */
public record Cenario(UUID id, String nome, int ano, int ultimoMesFechado,
    List<DreRegistro> base, Map<String,BigDecimal> variacaoPorGrupo, List<AjusteCenario> ajustes) {
    public Cenario {
        base=List.copyOf(base);
        variacaoPorGrupo=Map.copyOf(variacaoPorGrupo);
        ajustes=List.copyOf(ajustes);
    }
    public Cenario comVariacao(String grupo,BigDecimal variacao) {
        var novo=new HashMap<>(variacaoPorGrupo); novo.put(grupo,variacao);
        return new Cenario(id,nome,ano,ultimoMesFechado,base,novo,ajustes);
    }
    public Cenario comAjuste(AjusteCenario ajuste) {
        var novos=new ArrayList<>(ajustes);
        novos.removeIf(a->a.contaId().equals(ajuste.contaId())&&a.mes()==ajuste.mes());
        novos.add(ajuste);
        return new Cenario(id,nome,ano,ultimoMesFechado,base,variacaoPorGrupo,novos);
    }
}
