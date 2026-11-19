package br.jovemtech.itaqui.servico;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import br.jovemtech.itaqui.api.ApiException;
import br.jovemtech.itaqui.api.dto.MetaResposta;
import br.jovemtech.itaqui.api.dto.NoConta;
import br.jovemtech.itaqui.api.dto.NovaContaRequest;
import br.jovemtech.itaqui.api.dto.NovaPremissaRequest;
import br.jovemtech.itaqui.api.dto.PercentualPeriodo;
import br.jovemtech.itaqui.api.dto.PremissaResposta;
import br.jovemtech.itaqui.api.dto.VinculoRequest;
import br.jovemtech.itaqui.api.dto.VinculoResposta;
import br.jovemtech.itaqui.calculo.MotorCalculo;
import br.jovemtech.itaqui.dados.ArquivosBase;
import br.jovemtech.itaqui.dados.CarregadorBase;
import br.jovemtech.itaqui.dados.RelatorioImportacao;
import br.jovemtech.itaqui.dados.RepositorioBase;
import br.jovemtech.itaqui.dados.RepositorioCenarios;
import br.jovemtech.itaqui.dados.ValidacaoEstrutura;
import br.jovemtech.itaqui.dominio.Base;
import br.jovemtech.itaqui.dominio.Conta;
import br.jovemtech.itaqui.dominio.Periodo;
import br.jovemtech.itaqui.dominio.Premissa;
import br.jovemtech.itaqui.dominio.TipoConta;
import br.jovemtech.itaqui.dominio.Vinculo;

/** Consultas e alterações na base (estrutura da DRE, premissas, vínculos, importação). */
@Service
public class ServicoBase {

    public static final String FORMULA_EBITDA = "Receita Líquida (Receita Bruta − Deduções) − Custos − "
            + "Despesas Operacionais; Depreciação e Amortização ficam fora. Margem = EBITDA / Receita Líquida × 100";

    private final RepositorioBase repositorio;
    private final RepositorioCenarios cenarios;
    private final MotorCalculo motor;

    public ServicoBase(RepositorioBase repositorio, RepositorioCenarios cenarios, MotorCalculo motor) {
        this.repositorio = repositorio;
        this.cenarios = cenarios;
        this.motor = motor;
    }

    public Base base() {
        return repositorio.atual();
    }

    public void exigirAno(int ano) {
        if (!base().anos().contains(ano)) {
            throw ApiException.naoEncontrado("Não há dados para o ano " + ano + ". Anos disponíveis: "
                    + base().anos());
        }
    }

    // ------------------------------------------------------------------ meta

    public MetaResposta meta() {
        Base b = base();
        boolean ficticio = Base.ORIGEM_FICTICIO.equals(b.origem());
        return new MetaResposta("Plataforma Integrada de Projeção Orçamentária e EBITDA", b.dataCorte(),
                List.copyOf(b.anos()), b.origem(), ficticio,
                ficticio ? "DADOS FICTÍCIOS — DEMONSTRAÇÃO. Não representam valores da EMAP." : null,
                b.unidade(), b.versao(), b.fonte(), b.carregadaEm(), b.contas().size(), b.premissas().size(),
                b.vinculos().size(), cenarios.listar().size(), motor.regra().descricao(), FORMULA_EBITDA);
    }

    // ------------------------------------------------------------------ estrutura da DRE

    public List<NoConta> estrutura() {
        Base b = base();
        return b.raizes().stream().map(c -> no(b, c, 0)).toList();
    }

    private NoConta no(Base b, Conta c, int nivel) {
        List<NoConta> filhos = b.filhos(c.codigo()).stream().map(f -> no(b, f, nivel + 1)).toList();
        List<String> premissas = b.vinculos(c.codigo()).stream().map(Vinculo::codigoPremissa).toList();
        return new NoConta(c.codigo(), c.nome(), c.codigoPai(), c.tipo(), c.opex(), c.ordem(), nivel, c.folha(),
                b.temValores(c.codigo()), premissas, filhos);
    }

    public synchronized Conta criarConta(NovaContaRequest req) {
        Base b = base();
        String codigo = req.codigo().trim();
        if (b.conta(codigo).isPresent()) {
            throw ApiException.conflito("CONTA_DUPLICADA", "Já existe a conta " + codigo);
        }
        String pai = req.codigoPai() == null || req.codigoPai().isBlank() ? null : req.codigoPai().trim();
        int ordem = req.ordem() != null ? req.ordem()
                : b.contas().stream().mapToInt(Conta::ordem).max().orElse(0) + 1;
        Conta nova = new Conta(codigo, req.nome().trim(), pai, req.tipo(), Boolean.TRUE.equals(req.opex()), ordem);

        Map<String, Conta> contas = new LinkedHashMap<>();
        b.contas().forEach(c -> contas.put(c.codigo(), c));
        contas.put(codigo, nova);
        String problema = ValidacaoEstrutura.problemaDoPai(nova, contas);
        if (problema != null) {
            throw ApiException.regra("ESTRUTURA_INVALIDA", problema);
        }
        Map<String, Set<TipoConta>> tipos = ValidacaoEstrutura.tiposFolhaPorGrupo(contas);
        for (Map.Entry<String, Set<TipoConta>> e : tipos.entrySet()) {
            if (e.getValue().size() > 1) {
                throw ApiException.regra("ESTRUTURA_INVALIDA", "O grupo " + e.getKey()
                        + " passaria a misturar tipos " + e.getValue() + "; um grupo deve ter uma só natureza");
            }
        }
        repositorio.substituir(b.comConta(nova).comFonte(RepositorioBase.FONTE_EDITADA));
        return nova;
    }

    public synchronized void removerConta(String codigo) {
        Base b = base();
        if (b.conta(codigo).isEmpty()) {
            throw ApiException.naoEncontrado("Conta inexistente: " + codigo);
        }
        if (!b.filhos(codigo).isEmpty()) {
            throw ApiException.conflito("CONTA_COM_FILHAS", "A conta " + codigo + " tem contas filhas");
        }
        if (b.temValores(codigo)) {
            throw ApiException.conflito("CONTA_COM_VALORES", "A conta " + codigo
                    + " tem valores de orçado/realizado; para removê-la, importe uma nova base");
        }
        repositorio.substituir(b.semVinculos(v -> v.codigoConta().equals(codigo)).semConta(codigo)
                .comFonte(RepositorioBase.FONTE_EDITADA));
    }

    // ------------------------------------------------------------------ premissas

    public List<PremissaResposta> premissas() {
        Base b = base();
        return b.premissas().stream().map(p -> premissa(b, p)).toList();
    }

    public PremissaResposta premissa(String codigo) {
        Base b = base();
        return premissa(b, b.premissa(codigo)
                .orElseThrow(() -> ApiException.naoEncontrado("Premissa inexistente: " + codigo)));
    }

    private static PremissaResposta premissa(Base b, Premissa p) {
        List<PercentualPeriodo> valores = p.percentuais().entrySet().stream()
                .map(e -> new PercentualPeriodo(e.getKey(), e.getValue())).toList();
        List<String> contas = b.vinculos().stream().filter(v -> v.codigoPremissa().equals(p.codigo()))
                .map(Vinculo::codigoConta).toList();
        return new PremissaResposta(p.codigo(), p.nome(), valores, contas);
    }

    public synchronized PremissaResposta criarPremissa(NovaPremissaRequest req) {
        Base b = base();
        String codigo = req.codigo().trim();
        if (b.premissa(codigo).isPresent()) {
            throw ApiException.conflito("PREMISSA_DUPLICADA", "Já existe a premissa " + codigo);
        }
        Map<Periodo, BigDecimal> valores = new TreeMap<>();
        List<String> erros = new ArrayList<>();
        for (PercentualPeriodo v : req.valores()) {
            String problema = ValidacaoEstrutura.problemaPercentual(v.percentual());
            if (problema != null) {
                erros.add(v.periodo() + ": " + problema);
            } else if (valores.put(v.periodo(), v.percentual()) != null) {
                erros.add(v.periodo() + ": período repetido");
            }
        }
        if (!erros.isEmpty()) {
            throw ApiException.regra("PREMISSA_INVALIDA", "Valores inválidos na premissa", erros);
        }
        Premissa nova = Premissa.de(codigo, req.nome().trim(), valores);
        Base novaBase = b.comPremissa(nova).comFonte(RepositorioBase.FONTE_EDITADA);
        repositorio.substituir(novaBase);
        return premissa(novaBase, nova);
    }

    // ------------------------------------------------------------------ vínculos

    public List<VinculoResposta> vinculos() {
        Base b = base();
        return b.vinculos().stream().map(v -> vinculo(b, v)).toList();
    }

    private static VinculoResposta vinculo(Base b, Vinculo v) {
        return new VinculoResposta(v.codigoConta(), b.conta(v.codigoConta()).map(Conta::nome).orElse(null),
                v.codigoPremissa(), b.premissa(v.codigoPremissa()).map(Premissa::nome).orElse(null),
                v.vigenciaInicio());
    }

    public synchronized VinculoResposta criarVinculo(VinculoRequest req) {
        Base b = base();
        Conta conta = b.conta(req.codigoConta())
                .orElseThrow(() -> ApiException.regra("CONTA_INEXISTENTE", "Conta inexistente: " + req.codigoConta()));
        if (!conta.folha()) {
            throw ApiException.regra("CONTA_GRUPO", "A conta " + conta.codigo()
                    + " é GRUPO; vincule as contas filhas");
        }
        if (b.premissa(req.codigoPremissa()).isEmpty()) {
            throw ApiException.regra("PREMISSA_INEXISTENTE", "Premissa inexistente: " + req.codigoPremissa());
        }
        boolean existe = b.vinculos().stream().anyMatch(v -> v.codigoConta().equals(req.codigoConta())
                && v.codigoPremissa().equals(req.codigoPremissa()));
        if (existe) {
            throw ApiException.conflito("VINCULO_DUPLICADO", "A conta " + req.codigoConta()
                    + " já está vinculada a " + req.codigoPremissa());
        }
        Vinculo novo = new Vinculo(req.codigoConta(), req.codigoPremissa(), req.vigenciaInicio());
        Base nova = b.comVinculo(novo).comFonte(RepositorioBase.FONTE_EDITADA);
        repositorio.substituir(nova);
        return vinculo(nova, novo);
    }

    public synchronized void removerVinculo(String codigoConta, String codigoPremissa) {
        Base b = base();
        boolean existe = b.vinculos().stream().anyMatch(v -> v.codigoConta().equals(codigoConta)
                && v.codigoPremissa().equals(codigoPremissa));
        if (!existe) {
            throw ApiException.naoEncontrado("Vínculo inexistente: " + codigoConta + " → " + codigoPremissa);
        }
        repositorio.substituir(b.semVinculos(v -> v.codigoConta().equals(codigoConta)
                && v.codigoPremissa().equals(codigoPremissa)).comFonte(RepositorioBase.FONTE_EDITADA));
    }

    // ------------------------------------------------------------------ importação / exportação

    /**
     * Valida os arquivos e, se tudo estiver certo e {@code simular} for falso, substitui a
     * base inteira. Com qualquer erro, a base atual fica intacta.
     */
    public synchronized RelatorioImportacao importar(Map<String, String> arquivos, boolean simular) {
        CarregadorBase.Resultado r = repositorio.validar(arquivos, RepositorioBase.FONTE_IMPORTACAO);
        if (r.base() == null || simular) {
            return r.relatorio();
        }
        repositorio.substituir(r.base());
        return r.relatorio().comAplicado(true);
    }

    public synchronized MetaResposta restaurar() {
        repositorio.restaurarSemente();
        return meta();
    }

    public String exportarArquivo(String nome) {
        if (!ArquivosBase.TODOS.contains(nome)) {
            throw ApiException.naoEncontrado("Arquivo desconhecido: " + nome + ". Use um de " + ArquivosBase.TODOS);
        }
        return repositorio.exportar().get(nome);
    }
}
