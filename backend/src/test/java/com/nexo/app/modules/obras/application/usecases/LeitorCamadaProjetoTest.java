package com.nexo.app.modules.obras.application.usecases;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class LeitorCamadaProjetoTest {

    private final LeitorCamadaProjeto leitor = new LeitorCamadaProjeto();

    private static byte[] pdf(String... textos) throws IOException {
        try (PDDocument documento = new PDDocument()) {
            PDPage pagina = new PDPage(new PDRectangle(800, 600));
            documento.addPage(pagina);
            try (PDPageContentStream conteudo = new PDPageContentStream(documento, pagina)) {
                conteudo.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                for (String linha : textos) {
                    String[] partes = linha.split("\\|");
                    conteudo.beginText();
                    conteudo.newLineAtOffset(Float.parseFloat(partes[1]), Float.parseFloat(partes[2]));
                    conteudo.showText(partes[0]);
                    conteudo.endText();
                }
            }
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            documento.save(saida);
            return saida.toByteArray();
        }
    }

    @Test
    void leOsCodigosComAPosicaoNaFolhaEIgnoraOResto() throws IOException {
        LeitorCamadaProjeto.CamadaPagina camada = leitor.ler(pdf("D381272|400|300", "2#CAA 4 AWG|100|100", "PROJETO|50|50"), 1);

        assertThat(camada.candidatos()).hasSize(1);
        LeitorCamadaProjeto.Candidato candidato = camada.candidatos().get(0);
        assertThat(candidato.codigo()).isEqualTo("D381272");
        assertThat(candidato.x()).isCloseTo(0.5, within(0.001));
        assertThat(candidato.y()).isCloseTo(0.5, within(0.01));
    }

    @Test
    void ligaACaixaDeEstruturaAoCodigoProximo() throws IOException {
        LeitorCamadaProjeto.CamadaPagina camada = leitor.ler(
                pdf("D381272|400|335", "12/600|430|320", "; N1M|430|310", "; IM-R|430|300"), 1);

        assertThat(camada.candidatos()).hasSize(1);
        assertThat(camada.candidatos().get(0).estruturas()).containsExactly("12/600", "N1M", "IM-R");
    }

    @Test
    void caixaSemCodigoPertoViraPontoSemCodigo() throws IOException {
        LeitorCamadaProjeto.CamadaPagina camada = leitor.ler(pdf("12/600|430|320", "; N1M|430|310"), 1);

        assertThat(camada.candidatos()).hasSize(1);
        assertThat(camada.candidatos().get(0).codigo()).isNull();
        assertThat(camada.candidatos().get(0).estruturas()).containsExactly("12/600", "N1M");
    }

    @Test
    void pdfSemCodigosDevolveListaVazia() throws IOException {
        assertThat(leitor.ler(pdf("FOLHA 1|50|50"), 1).candidatos()).isEmpty();
    }

    @Test
    void interpretaAEscalaDoCarimbo() {
        assertThat(LeitorCamadaProjeto.interpretarEscala("ESCALA:1:1.000")).isEqualTo(1000);
        assertThat(LeitorCamadaProjeto.interpretarEscala("escala 1 : 500")).isEqualTo(500);
        assertThat(LeitorCamadaProjeto.interpretarEscala("sem indicação")).isNull();
    }

    @Test
    void paginaInexistenteERecusada() throws IOException {
        byte[] arquivo = pdf("D381272|400|300");

        assertThatThrownBy(() -> leitor.ler(arquivo, 2)).hasMessageContaining("página 2");
    }

    @Test
    void renderizaAFolhaInteiraNumaImagem() throws IOException {
        RenderizadorPaginaProjeto.Bloco folha = new RenderizadorPaginaProjeto().renderizarInteira(pdf("D381272|400|300"), 1);

        assertThat(folha.largura()).isEqualTo(1);
        assertThat(java.util.Base64.getDecoder().decode(folha.base64Jpeg())).startsWith((byte) 0xFF, (byte) 0xD8);
    }
}
