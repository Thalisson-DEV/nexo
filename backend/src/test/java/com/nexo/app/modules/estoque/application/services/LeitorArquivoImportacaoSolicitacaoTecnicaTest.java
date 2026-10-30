package com.nexo.app.modules.estoque.application.services;

import com.nexo.app.shared.domain.exception.PreenchimentoInvalidoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeitorArquivoImportacaoSolicitacaoTecnicaTest {

    private final ModeloImportacaoSolicitacaoTecnicaService modelos =
            new ModeloImportacaoSolicitacaoTecnicaService();
    private final LeitorArquivoImportacaoSolicitacaoTecnica leitor =
            new LeitorArquivoImportacaoSolicitacaoTecnica();

    @Test
    void leModeloCsvComBomSemAvaliarConteudo() {
        var csv = leitor.ler(arquivo("modelo.csv", modelos.gerarCsv()));

        assertThat(csv.linhas()).singleElement().satisfies(linha ->
                assertThat(linha.valor("Tipo Movimentacao")).isEqualTo("221"));
    }

    @Test
    void leArquivoComCabecalhoLegadoDeTrezeColunas() {
        String csvLegado = String.join(";", LeitorArquivoImportacaoSolicitacaoTecnica.CABECALHOS_LEGADO)
                + "\nSOL-001;OBRA-1;;ALMOX;;;;221;MAT-1;5.000;;;\n";
        var lido = leitor.ler(arquivo("legado.csv", csvLegado.getBytes(StandardCharsets.UTF_8)));

        assertThat(lido.linhas()).singleElement().satisfies(linha -> {
            assertThat(linha.valor("Tipo Movimentacao")).isEqualTo("221");
            assertThat(linha.valor("Numero Obra")).isEqualTo("OBRA-1");
        });
    }

    @Test
    void marcaFormulaTextualSemAvaliaLa() {
        String csv = String.join(";", ModeloImportacaoSolicitacaoTecnicaService.CABECALHOS)
                + "\nOBRA-1;;ALMOX;;;;221;MAT-1;=1+1;;;\n";
        var lido = leitor.ler(arquivo("formula.csv", csv.getBytes(StandardCharsets.UTF_8)));
        assertThat(lido.linhas().getFirst().camposFormula()).containsExactly("Quantidade");
    }

    @Test
    @DisplayName("Rejeita extensão XLSX e qualquer formato diferente de .csv")
    void rejeitaXlsxEExtensoesNaoCsv() {
        assertThatThrownBy(() -> leitor.ler(arquivo("modelo.xlsx", new byte[]{1, 2, 3, 4})))
                .isInstanceOf(PreenchimentoInvalidoException.class)
                .hasMessageContaining("Formato nao suportado");

        assertThatThrownBy(() -> leitor.ler(arquivo("modelo.txt", modelos.gerarCsv())))
                .isInstanceOf(PreenchimentoInvalidoException.class)
                .hasMessageContaining("Formato nao suportado");
    }

    @Test
    void rejeitaArquivoCorrompidoTamanhoExcedidoELinhasExcedidas() {
        assertThatThrownBy(() -> leitor.ler(arquivo("corrompido.csv", new byte[]{1, 2, 3})))
                .isInstanceOf(PreenchimentoInvalidoException.class);

        byte[] grande = new byte[(int) LeitorArquivoImportacaoSolicitacaoTecnica.TAMANHO_MAXIMO + 1];
        assertThatThrownBy(() -> leitor.ler(arquivo("grande.csv", grande)))
                .isInstanceOf(PreenchimentoInvalidoException.class)
                .hasMessageContaining("5 MiB");

        StringBuilder csv = new StringBuilder(String.join(";",
                ModeloImportacaoSolicitacaoTecnicaService.CABECALHOS)).append('\n');
        for (int indice = 0; indice <= LeitorArquivoImportacaoSolicitacaoTecnica.MAXIMO_LINHAS; indice++) {
            csv.append("OBRA-1;;ALMOX;;;;221;MAT-1;1;;;\n");
        }
        assertThatThrownBy(() -> leitor.ler(arquivo(
                "linhas.csv", csv.toString().getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(PreenchimentoInvalidoException.class)
                .hasMessageContaining("2.000");
    }

    @Test
    void rejeitaCsvComSeparadorIncorretoEFormulaTextual() {
        String virgula = String.join(",", ModeloImportacaoSolicitacaoTecnicaService.CABECALHOS)
                + "\nOBRA-1,,ALMOX,,,,221,MAT-1,1,,,\n";
        assertThatThrownBy(() -> leitor.ler(arquivo(
                "virgula.csv", virgula.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(PreenchimentoInvalidoException.class);

        String formula = String.join(";", ModeloImportacaoSolicitacaoTecnicaService.CABECALHOS)
                + "\nOBRA-1;;ALMOX;;;;221;MAT-1;=1+1;;;\n";
        var lido = leitor.ler(arquivo("formula.csv", formula.getBytes(StandardCharsets.UTF_8)));
        assertThat(lido.linhas().getFirst().camposFormula()).containsExactly("Quantidade");
    }

    private MockMultipartFile arquivo(String nome, byte[] bytes) {
        return new MockMultipartFile("arquivo", nome, "application/octet-stream", bytes);
    }
}
