package br.com.jovemtech.dre.forecast;
import java.util.List;
import java.util.UUID;
public record ResultadoForecast(UUID cenarioId,String nome,int ano,int ultimoMesFechado,
    List<LinhaForecast> linhas,List<ResumoMensal> meses,List<String> avisos) { }
