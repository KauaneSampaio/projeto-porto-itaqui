package br.com.jovemtech.dre.forecast;
import java.math.BigDecimal;
import java.util.List;
/**
 * Para dados GFIN os custos/despesas ficam negativos como na planilha. Receita é bruta.
 * Para legados, custo e opex mantêm a convenção antiga (positivo).
 */
public record ResumoMensal(int mes,String status,String statusOrcamento,BigDecimal receita,BigDecimal custo,
    BigDecimal opex,BigDecimal ebitdaDemonstrativo,BigDecimal ebitdaOrcamento,
    BigDecimal desvioEbitda,List<String> pendencias,
    BigDecimal deducoes,BigDecimal receitaLiquida,BigDecimal despesas,
    BigDecimal margemEbitda,BigDecimal receitaLiquidaOrcamento,BigDecimal margemEbitdaOrcamento,
    String convencaoSinais) { }
