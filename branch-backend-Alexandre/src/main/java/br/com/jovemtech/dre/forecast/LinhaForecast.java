package br.com.jovemtech.dre.forecast;
import java.math.BigDecimal;
public record LinhaForecast(String contaId,String descricao,String grupo,int mes,
    String origem,BigDecimal valorBase,BigDecimal valorForecast,boolean contaAnalitica,
    String situacao) { }
