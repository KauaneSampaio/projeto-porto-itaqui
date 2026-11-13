package br.com.jovemtech.dre.service;

import br.com.jovemtech.dre.forecast.*;
import br.com.jovemtech.dre.model.*;
import br.com.jovemtech.dre.repository.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CenarioService {
    private final CenarioRepository cenarios;
    private final DreRepository dre;
    public CenarioService(CenarioRepository cenarios,DreRepository dre) {this.cenarios=cenarios;this.dre=dre;}

    public Cenario criar(String nome,int ano,int ultimoMesFechado) {
        if(nome==null||nome.isBlank()||nome.length()>120)throw new RegraNegocioException("nome deve ter de 1 a 120 caracteres");
        if(ano<2000||ano>2100)throw new RegraNegocioException("ano fora do intervalo permitido");
        if(ultimoMesFechado<0||ultimoMesFechado>12)throw new RegraNegocioException("ultimoMesFechado deve estar entre 0 e 12");
        List<DreRegistro> snapshot=dre.listar().stream().filter(r->r.ano()==ano).toList();
        if(snapshot.isEmpty())throw new RegraNegocioException("Importe dados do ano antes de criar o cenário");
        boolean gfin=snapshot.stream().anyMatch(r->r.natureza().name().startsWith("GFIN_"));
        boolean legado=snapshot.stream().anyMatch(r->!r.natureza().name().startsWith("GFIN_"));
        if(gfin&&legado)throw new RegraNegocioException("Dados GFIN e demonstração misturados. Reimporte pelo endpoint GFIN com substituirBase=true.");
        if(gfin && ultimoMesFechado>0 && snapshot.stream().noneMatch(r->r.mes()==ultimoMesFechado&&r.perspectiva()==Perspectiva.REALIZADO))
            throw new RegraNegocioException("Sem REALIZADO para o mês declarado como último mês fechado: "+ultimoMesFechado);
        Cenario c=new Cenario(UUID.randomUUID(),nome.trim(),ano,ultimoMesFechado,snapshot,Map.of(),List.of());
        cenarios.salvar(c);return c;
    }
    public List<Cenario> listar(){return cenarios.listar();}
    public Cenario buscar(UUID id){return cenarios.buscar(id).orElseThrow(()->new RecursoNaoEncontradoException("Cenário não encontrado: "+id));}

    public synchronized Cenario alterarVariacao(UUID id,String grupo,BigDecimal percentual) {
        Cenario c=buscar(id);
        if(grupo==null||grupo.isBlank()||percentual==null)
            throw new RegraNegocioException("grupo e variacaoPercentual são obrigatórios");
        grupo=grupo.trim().toUpperCase(Locale.ROOT);
        if(percentual.compareTo(new BigDecimal("-100"))<0)
            throw new RegraNegocioException("A variação não pode ser menor que -100%");
        final String grupoBusca=grupo;
        if(c.base().stream().noneMatch(r->r.grupo().equals(grupoBusca)))
            throw new RegraNegocioException("Grupo inexistente na base do cenário: "+grupo);
        Cenario alterado=c.comVariacao(grupo,percentual);
        cenarios.salvar(alterado);return alterado;
    }
    public synchronized Cenario definirAjuste(UUID id,String contaId,int mes,BigDecimal valorFinal) {
        Cenario c=buscar(id);
        if(contaId==null||contaId.isBlank()||valorFinal==null)
            throw new RegraNegocioException("contaId e valorFinal são obrigatórios");
        if(mes<1||mes>12)throw new RegraNegocioException("mes deve estar entre 1 e 12");
        if(mes<=c.ultimoMesFechado())throw new RegraNegocioException("Período fechado: ajustes são permitidos apenas após o último mês fechado");
        String idConta=contaId.trim();
        if(c.base().stream().noneMatch(r->r.contaId().equals(idConta)&&r.mes()==mes&&r.perspectiva()==Perspectiva.PROJETADO))
            throw new RegraNegocioException("Não há registro PROJETADO para a conta "+idConta+" no mês "+mes);
        Cenario alterado=c.comAjuste(new AjusteCenario(idConta,mes,valorFinal));
        cenarios.salvar(alterado);return alterado;
    }
    public ResultadoForecast calcular(UUID id){return ForecastEngine.calcular(buscar(id));}
}
