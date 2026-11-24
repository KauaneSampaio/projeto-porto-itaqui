package br.jovemtech.itaqui.dados;

import java.util.List;

import br.jovemtech.itaqui.dominio.Periodo;

/**
 * Resultado da validação de uma base. Se {@code valido} for falso, nada foi incorporado
 * (RNF001: nenhum registro inválido entra silenciosamente).
 */
public record RelatorioImportacao(
        boolean valido,
        boolean aplicado,
        List<ErroValidacao> erros,
        List<ErroValidacao> avisos,
        Resumo resumo) {

    public record Resumo(int contas, int valores, int premissas, int vinculos, Periodo dataCorte) {
    }

    public RelatorioImportacao comAplicado(boolean foiAplicado) {
        return new RelatorioImportacao(valido, foiAplicado, erros, avisos, resumo);
    }
}
