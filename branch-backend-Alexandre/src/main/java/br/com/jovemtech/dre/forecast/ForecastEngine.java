package br.com.jovemtech.dre.forecast;

import br.com.jovemtech.dre.model.*;
import java.math.*;
import java.util.*;

/** Motor GFIN com sinais originais e roll-up analítico; preserva funcionamento do CSV legado. */
public final class ForecastEngine {
    private static final Set<String> GRUPOS_GFIN=Set.of("RECEITA","DEDUCAO","CUSTO","DESPESA");
    private static final Set<String> GRUPOS_LEGADO=Set.of("RECEITA","CUSTO","OPEX");
    private static final BigDecimal CEM=new BigDecimal("100");
    private ForecastEngine() { }
    private record Chave(String contaId,int mes,Perspectiva perspectiva) { }
    private record ChaveAjuste(String contaId,int mes) { }
    private static BigDecimal soma(Map<String,BigDecimal> m,String...grupos) {
        BigDecimal valor=BigDecimal.ZERO;
        for(String g:grupos)valor=valor.add(m.getOrDefault(g,BigDecimal.ZERO));
        return valor;
    }
    private static BigDecimal margem(BigDecimal ebitda,BigDecimal liquida) {
        if(ebitda==null||liquida==null||liquida.signum()==0)return null;
        return ebitda.divide(liquida,8,RoundingMode.HALF_UP);
    }

    public static ResultadoForecast calcular(Cenario c) {
        Map<Chave,DreRegistro> indice=new HashMap<>();
        Map<String,DreRegistro> contas=new TreeMap<>();
        Set<String> contasPai=new HashSet<>();
        boolean gfin=c.base().stream().anyMatch(r->r.natureza().name().startsWith("GFIN_"));
        if(gfin && c.base().stream().anyMatch(r->!r.natureza().name().startsWith("GFIN_")))
            throw new IllegalArgumentException("Não misture dados GFIN com dados demonstrativos");
        Set<String> gruposValidos=gfin?GRUPOS_GFIN:GRUPOS_LEGADO;
        for(DreRegistro r:c.base()) {
            if(r.ano()!=c.ano())continue;
            indice.put(new Chave(r.contaId(),r.mes(),r.perspectiva()),r);
            contas.putIfAbsent(r.contaId(),r);
            if(r.contaPaiId()!=null&&!r.contaPaiId().isBlank())contasPai.add(r.contaPaiId());
        }
        Map<ChaveAjuste,BigDecimal> ajustes=new HashMap<>();
        for(AjusteCenario ajuste:c.ajustes())ajustes.put(new ChaveAjuste(ajuste.contaId(),ajuste.mes()),ajuste.valorFinal());
        List<LinhaForecast> linhas=new ArrayList<>();
        List<ResumoMensal> resumos=new ArrayList<>();
        List<String> avisos=new ArrayList<>();
        if(gfin) {
            avisos.add("GFIN: RECEITA + DEDUCAO + CUSTO + DESPESA; deduções, custos e despesas com sinal original da planilha.");
            avisos.add("2026 set-dez: PROJETADO usa orçamento como semente, NÃO é realizado nem projeção homologada.");
            avisos.add("Divergências da fonte, inclusive EBITDA orçado de março/dezembro de 2026, estão no relatório de auditoria.");
            avisos.add("2027: valores auxiliares não constituem orçamento aprovado. Unidade monetária não indicada na fonte.");
        } else avisos.add("Modo legado: EBITDA demonstrativo RECEITA - CUSTO - OPEX (custos positivos).");
        avisos.add("Cenários são snapshots independentes; importar novos dados não atualiza cenários existentes.");
        for(int mes=1;mes<=12;mes++) {
            List<String> falta=new ArrayList<>();
            List<String> faltaOrc=new ArrayList<>();
            Map<String,BigDecimal> categorias=new HashMap<>();
            Map<String,BigDecimal> categoriasOrc=new HashMap<>();
            Perspectiva origem=mes<=c.ultimoMesFechado()?Perspectiva.REALIZADO:Perspectiva.PROJETADO;
            for(DreRegistro conta:contas.values()) {
                boolean analitica=!contasPai.contains(conta.contaId());
                DreRegistro dado=indice.get(new Chave(conta.contaId(),mes,origem));
                BigDecimal valorBase=dado==null?null:dado.valor();
                BigDecimal previsto=null;
                if(dado!=null) {
                    previsto=valorBase;
                    if(origem==Perspectiva.PROJETADO) {
                        ChaveAjuste chave=new ChaveAjuste(conta.contaId(),mes);
                        if(ajustes.containsKey(chave)) previsto=ajustes.get(chave);
                        else {
                            BigDecimal percentual=c.variacaoPorGrupo().getOrDefault(conta.grupo(),BigDecimal.ZERO);
                            previsto=valorBase.multiply(BigDecimal.ONE.add(percentual.divide(CEM,10,RoundingMode.HALF_UP)))
                                .setScale(8,RoundingMode.HALF_UP);
                        }
                    }
                }
                linhas.add(new LinhaForecast(conta.contaId(),conta.descricao(),conta.grupo(),mes,
                    origem.name(),valorBase,previsto,analitica,dado==null?"SEM_DADO":"OK"));
                if(!analitica)continue;
                if(dado==null) falta.add("Sem "+origem+" para conta "+conta.contaId()+" mês "+mes);
                else if(!gruposValidos.contains(conta.grupo()))
                    falta.add("Grupo não mapeado: "+conta.grupo()+" conta "+conta.contaId());
                else categorias.merge(conta.grupo(),previsto,BigDecimal::add);
                DreRegistro orc=indice.get(new Chave(conta.contaId(),mes,Perspectiva.ORCAMENTO));
                if(orc==null) faltaOrc.add("Sem ORCAMENTO para conta "+conta.contaId()+" mês "+mes);
                else if(gruposValidos.contains(conta.grupo()))categoriasOrc.merge(conta.grupo(),orc.valor(),BigDecimal::add);
            }
            for(String grupo:gruposValidos) {
                boolean existe=contas.values().stream().anyMatch(r->!contasPai.contains(r.contaId())&&grupo.equals(r.grupo()));
                if(!existe) {
                    falta.add("Grupo obrigatório ausente: "+grupo);
                    faltaOrc.add("Grupo obrigatório ausente no orçamento: "+grupo);
                }
            }
            boolean ok=falta.isEmpty();
            boolean okOrc=faltaOrc.isEmpty() && !categoriasOrc.isEmpty() && gruposValidos.stream().allMatch(categoriasOrc::containsKey);
            falta.addAll(faltaOrc);
            BigDecimal receita=ok?categorias.get("RECEITA"):null;
            BigDecimal custo=ok?categorias.get("CUSTO"):null;
            BigDecimal opex=ok?(gfin?categorias.get("DESPESA"):categorias.get("OPEX")):null;
            BigDecimal deducoes=gfin&&ok?categorias.get("DEDUCAO"):null;
            BigDecimal liquida=gfin&&ok?soma(categorias,"RECEITA","DEDUCAO"):null;
            BigDecimal despesas=gfin&&ok?categorias.get("DESPESA"):null;
            BigDecimal ebitda=ok?(gfin?soma(categorias,"RECEITA","DEDUCAO","CUSTO","DESPESA"):
                receita.subtract(custo).subtract(opex)):null;
            BigDecimal ebitdaOrc=okOrc?(gfin?soma(categoriasOrc,"RECEITA","DEDUCAO","CUSTO","DESPESA"):
                categoriasOrc.get("RECEITA").subtract(categoriasOrc.get("CUSTO")).subtract(categoriasOrc.get("OPEX"))):null;
            BigDecimal liquidaOrc=gfin&&okOrc?soma(categoriasOrc,"RECEITA","DEDUCAO"):null;
            resumos.add(new ResumoMensal(mes,ok?"COMPLETO":"INCOMPLETO",okOrc?"COMPLETO":"INCOMPLETO",
                receita,custo,opex,ebitda,ebitdaOrc,(ok&&okOrc)?ebitda.subtract(ebitdaOrc):null,
                List.copyOf(falta),deducoes,liquida,despesas,margem(ebitda,liquida),liquidaOrc,
                margem(ebitdaOrc,liquidaOrc),gfin?"GFIN_DESPESAS_NEGATIVAS":"LEGADO_DESPESAS_POSITIVAS"));
        }
        return new ResultadoForecast(c.id(),c.nome(),c.ano(),c.ultimoMesFechado(),
            List.copyOf(linhas),List.copyOf(resumos),List.copyOf(avisos));
    }
}
