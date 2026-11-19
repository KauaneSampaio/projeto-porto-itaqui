package br.jovemtech.itaqui.dados;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.jovemtech.itaqui.dominio.Base;

import static org.assertj.core.api.Assertions.assertThat;

/** CT-07 Integridade dos dados: registros inválidos são recusados, sem carga parcial. */
class CarregadorBaseTest {

    private final CarregadorBase carregador = new CarregadorBase();

    private CarregadorBase.Resultado carregarCom(String arquivo, String conteudo) {
        Map<String, String> arquivos = ArquivosSemente.ler();
        arquivos.put(arquivo, conteudo);
        return carregador.carregar(arquivos, "TESTE");
    }

    private CarregadorBase.Resultado acrescentar(String arquivo, String linha) {
        Map<String, String> arquivos = ArquivosSemente.ler();
        return carregarCom(arquivo, arquivos.get(arquivo) + linha + "\n");
    }

    private static void assertErro(CarregadorBase.Resultado r, String arquivo, String trecho) {
        assertThat(r.base()).as("base não deve ser carregada").isNull();
        assertThat(r.relatorio().valido()).isFalse();
        assertThat(r.relatorio().erros())
                .anySatisfy(e -> {
                    assertThat(e.arquivo()).isEqualTo(arquivo);
                    assertThat(e.mensagem()).contains(trecho);
                });
    }

    @Test
    @DisplayName("A base fictícia do projeto é válida e marcada como FICTICIO")
    void sementeValida() {
        CarregadorBase.Resultado r = carregador.carregar(ArquivosSemente.ler(), "TESTE");
        assertThat(r.relatorio().erros()).isEmpty();
        Base b = r.base();
        assertThat(b).isNotNull();
        assertThat(b.origem()).isEqualTo(Base.ORIGEM_FICTICIO);
        assertThat(b.anos()).containsExactly(2024, 2025, 2026, 2027);
        assertThat(b.dataCorte()).hasToString("2026-09");
    }

    @Test
    @DisplayName("Gravar e ler de volta produz a mesma base")
    void idaEVolta() {
        Base original = carregador.carregar(ArquivosSemente.ler(), "TESTE").base();
        Map<String, String> escritos = new CsvEscritor().escrever(original);
        Base lida = carregador.carregar(escritos, "TESTE").base();
        assertThat(lida).isNotNull();
        assertThat(lida.contas()).isEqualTo(original.contas());
        assertThat(lida.valores()).hasSameSizeAs(original.valores());
        assertThat(lida.premissas()).isEqualTo(original.premissas());
        assertThat(lida.vinculos()).isEqualTo(original.vinculos());
    }

    @Test
    void realizadoEmMesAberto() {
        assertErro(acrescentar("valores.csv", "3.1;2026;12;REALIZADO;10,00"), "valores.csv", "mês ainda aberto");
    }

    @Test
    void contaInexistente() {
        assertErro(acrescentar("valores.csv", "9.9;2026;12;ORCADO;10,00"), "valores.csv", "Conta inexistente");
    }

    @Test
    void valorEmGrupo() {
        assertErro(acrescentar("valores.csv", "3;2027;1;ORCADO;10"), "valores.csv", "é GRUPO");
    }

    @Test
    void valorNegativo() {
        assertErro(acrescentar("valores.csv", "3.1;2028;1;ORCADO;-5"), "valores.csv", "negativo");
    }

    @Test
    void valorNaoNumerico() {
        assertErro(acrescentar("valores.csv", "3.1;2028;1;ORCADO;abc"), "valores.csv", "não numérico");
    }

    @Test
    void mesInvalido() {
        assertErro(acrescentar("valores.csv", "3.1;2028;13;ORCADO;1"), "valores.csv", "Mês inválido");
    }

    @Test
    void lancamentoDuplicado() {
        assertErro(acrescentar("valores.csv", "3.1;2024;1;ORCADO;1"), "valores.csv", "duplicado");
    }

    @Test
    void tipoDeContaDesconhecido() {
        assertErro(acrescentar("contas.csv", "8;Outra;;LUCRO;N;99"), "contas.csv", "Tipo desconhecido");
    }

    @Test
    void paiInexistente() {
        assertErro(acrescentar("contas.csv", "8.1;Outra;8;CUSTO;N;99"), "contas.csv", "pai inexistente");
    }

    @Test
    void grupoMisturandoTipos() {
        assertErro(acrescentar("contas.csv", "5.9;Receita no grupo de custos;5;RECEITA_BRUTA;N;99"),
                "contas.csv", "mistura");
    }

    @Test
    void vinculoParaPremissaInexistente() {
        assertErro(acrescentar("vinculos.csv", "6.4;SELIC;2024-01"), "vinculos.csv", "Premissa inexistente");
    }

    @Test
    void percentualAbsurdo() {
        assertErro(acrescentar("premissas.csv", "IPCA;IPCA acumulado;2028;1;-100"), "premissas.csv", "-100");
    }

    @Test
    void semDataDeCorte() {
        assertErro(carregarCom("config.csv", "chave;valor\norigem;FICTICIO\n"), "config.csv", "data_corte");
    }

    @Test
    void arquivoAusente() {
        assertErro(carregarCom("vinculos.csv", ""), "vinculos.csv", "ausente");
    }

    @Test
    void cabecalhoErrado() {
        assertErro(carregarCom("contas.csv", "codigo;descricao\n1;x\n"), "contas.csv", "Coluna(s) obrigatória(s)");
    }

    @Test
    @DisplayName("Decimal com ponto também é aceito")
    void decimalComPonto() {
        CarregadorBase.Resultado r = acrescentar("valores.csv", "3.1;2028;1;ORCADO;1234.56");
        assertThat(r.relatorio().erros()).isEmpty();
        assertThat(r.base().valor("3.1", new br.jovemtech.itaqui.dominio.Periodo(2028, 1),
                br.jovemtech.itaqui.dominio.TipoValor.ORCADO)).isEqualByComparingTo("1234.56");
    }
}
