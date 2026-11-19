package br.jovemtech.itaqui.api.dto;

import java.util.List;

import br.jovemtech.itaqui.dominio.TipoConta;

/** Nó da árvore da DRE. */
public record NoConta(String codigo, String nome, String codigoPai, TipoConta tipo, boolean opex, int ordem,
        int nivel, boolean folha, boolean temValores, List<String> premissas, List<NoConta> filhos) {
}
