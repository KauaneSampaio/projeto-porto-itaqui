package br.com.jovemtech.dre.model;
import java.math.BigDecimal;
public record DreRegistro(String contaId, String contaPaiId, String descricao, String grupo, int ano, int mes, Perspectiva perspectiva, BigDecimal valor, NaturezaDado natureza) {}
