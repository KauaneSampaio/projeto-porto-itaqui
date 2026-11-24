package br.jovemtech.itaqui.dominio;

/** Liga uma conta folha a uma premissa a partir de um mês. */
public record Vinculo(String codigoConta, String codigoPremissa, Periodo vigenciaInicio) {

    public boolean vigente(Periodo periodo) {
        return !vigenciaInicio.depoisDe(periodo);
    }
}
