package br.jovemtech.itaqui.dados;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import br.jovemtech.itaqui.dominio.Conta;
import br.jovemtech.itaqui.dominio.TipoConta;

/** Regras de estrutura usadas tanto na importação quanto no cadastro pela API. */
public final class ValidacaoEstrutura {

    private static final BigDecimal MENOS_CEM = BigDecimal.valueOf(-100);
    private static final BigDecimal MIL = BigDecimal.valueOf(1000);

    private ValidacaoEstrutura() {
    }

    /** Devolve a mensagem de erro do vínculo com o pai, ou {@code null} se estiver tudo certo. */
    public static String problemaDoPai(Conta conta, Map<String, Conta> contas) {
        if (conta.codigoPai() == null) {
            return null;
        }
        Conta pai = contas.get(conta.codigoPai());
        if (pai == null) {
            return "Conta pai inexistente: '" + conta.codigoPai() + "'";
        }
        if (pai.tipo() != TipoConta.GRUPO) {
            return "Conta pai " + pai.codigo() + " não é GRUPO; só grupos podem ter filhas";
        }
        Set<String> visitados = new HashSet<>();
        Conta atual = conta;
        while (atual != null && atual.codigoPai() != null) {
            if (!visitados.add(atual.codigo())) {
                return "Hierarquia circular envolvendo a conta " + conta.codigo();
            }
            atual = contas.get(atual.codigoPai());
        }
        return null;
    }

    /** Para cada grupo, os tipos das contas folha abaixo dele (em qualquer nível). */
    public static Map<String, Set<TipoConta>> tiposFolhaPorGrupo(Map<String, Conta> contas) {
        Map<String, Set<TipoConta>> resultado = new HashMap<>();
        for (Conta c : contas.values()) {
            if (!c.folha()) {
                continue;
            }
            Set<String> visitados = new HashSet<>();
            String pai = c.codigoPai();
            while (pai != null && visitados.add(pai)) {
                resultado.computeIfAbsent(pai, k -> EnumSet.noneOf(TipoConta.class)).add(c.tipo());
                Conta p = contas.get(pai);
                pai = p == null ? null : p.codigoPai();
            }
        }
        return resultado;
    }

    /** Percentual de premissa aceitável: maior que -100% e menor que 1000%. */
    public static String problemaPercentual(BigDecimal pct) {
        if (pct.compareTo(MENOS_CEM) <= 0) {
            return "Percentual deve ser maior que -100";
        }
        if (pct.compareTo(MIL) >= 0) {
            return "Percentual deve ser menor que 1000";
        }
        return null;
    }
}
