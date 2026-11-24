package br.jovemtech.itaqui.dominio;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Mês de competência (ano + mês). Texto no formato AAAA-MM. */
public record Periodo(int ano, int mes) implements Comparable<Periodo> {

    public Periodo {
        if (mes < 1 || mes > 12) {
            throw new IllegalArgumentException("Mês inválido: " + mes + " (use 1 a 12)");
        }
        if (ano < 1900 || ano > 2200) {
            throw new IllegalArgumentException("Ano inválido: " + ano);
        }
    }

    @JsonCreator
    public static Periodo parse(String texto) {
        if (texto == null) {
            throw new IllegalArgumentException("Período vazio (use AAAA-MM)");
        }
        String[] partes = texto.trim().split("-");
        if (partes.length != 2) {
            throw new IllegalArgumentException("Período inválido: '" + texto + "' (use AAAA-MM)");
        }
        try {
            return new Periodo(Integer.parseInt(partes[0].trim()), Integer.parseInt(partes[1].trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Período inválido: '" + texto + "' (use AAAA-MM)");
        }
    }

    private int indice() {
        return ano * 12 + (mes - 1);
    }

    public boolean depoisDe(Periodo outro) {
        return compareTo(outro) > 0;
    }

    @Override
    public int compareTo(Periodo o) {
        return Integer.compare(indice(), o.indice());
    }

    @JsonValue
    @Override
    public String toString() {
        return String.format("%04d-%02d", ano, mes);
    }
}
