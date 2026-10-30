package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderizacaoAtaDocxServiceTest {
    private static final Path REFERENCIAS = Path.of("docs", "references for CIPA and Brigada domain");
    private final RenderizacaoAtaDocxService service = new RenderizacaoAtaDocxService();

    @Test
    @DisplayName("CIPA preserva arte do cabeçalho e recebe somente presentes na tabela de assinatura")
    void deveRenderizarCipaPreservandoPacote() throws Exception {
        Path base = REFERENCIAS.resolve("Ata de Reunião - CIPA 2025-2026.docx");
        byte[] gerado;
        try (InputStream entrada = Files.newInputStream(base)) {
            gerado = service.renderizar(entrada, dados(TipoCorpoGovernancaSst.CIPA));
        }

        try (XWPFDocument referencia = new XWPFDocument(Files.newInputStream(base));
             XWPFDocument resultado = new XWPFDocument(new ByteArrayInputStream(gerado))) {
            assertEquals(referencia.getAllPackagePictures().size(), resultado.getAllPackagePictures().size());
            assertEquals(referencia.getHeaderList().size(), resultado.getHeaderList().size());
            assertTrue(resultado.getParagraphs().stream().anyMatch(p -> p.getText().contains("ATA Nº 08/2026")));
            assertTrue(resultado.getTables().getFirst().getText().contains("Pessoa Presente"));
            assertTrue(!resultado.getTables().getFirst().getText().contains("Pessoa Ausente"));
        }
    }

    @Test
    @DisplayName("Brigada preserva os dois logotipos e produz tabela de assinatura")
    void deveRenderizarBrigadaPreservandoPacote() throws Exception {
        Path base = REFERENCIAS.resolve("Ata de Reunião - Brigada 2025-2026.docx");
        byte[] gerado;
        try (InputStream entrada = Files.newInputStream(base)) {
            gerado = service.renderizar(entrada, dados(TipoCorpoGovernancaSst.BRIGADA));
        }

        try (XWPFDocument referencia = new XWPFDocument(Files.newInputStream(base));
             XWPFDocument resultado = new XWPFDocument(new ByteArrayInputStream(gerado))) {
            assertEquals(2, resultado.getAllPackagePictures().size());
            assertEquals(referencia.getAllPackagePictures().size(), resultado.getAllPackagePictures().size());
            assertTrue(resultado.getParagraphs().stream()
                    .anyMatch(p -> p.getText().contains("BRIGADA DE INCÊNDIO")));
            assertTrue(resultado.getTables().getFirst().getText().contains("ASSINATURA"));
        }
    }

    @Test
    @DisplayName("duração inválida é recusada antes de tocar no pacote")
    void deveRecusarDuracaoInvalida() throws Exception {
        var valido = dados(TipoCorpoGovernancaSst.CIPA);
        var invalido = new RenderizacaoAtaDocxService.DadosAta(valido.tipoCorpo(), valido.numeroAta(),
                valido.tipoReuniao(), valido.ciclo(), valido.inicio(), valido.inicio(),
                valido.conteudoAta(), valido.participantes());
        try (InputStream entrada = Files.newInputStream(
                REFERENCIAS.resolve("Ata de Reunião - CIPA 2025-2026.docx"))) {
            assertThrows(InvarianteVioladaException.class, () -> service.renderizar(entrada, invalido));
        }
    }

    private RenderizacaoAtaDocxService.DadosAta dados(TipoCorpoGovernancaSst tipo) {
        OffsetDateTime inicio = OffsetDateTime.parse("2026-06-26T14:10:00-03:00");
        return new RenderizacaoAtaDocxService.DadosAta(tipo, "08/2026", "REUNIÃO ORDINÁRIA",
                "2025/2026", inicio, inicio.plusMinutes(90),
                "A reunião foi iniciada com os dados confirmados. Foram discutidos os itens constantes da transcrição normalizada e revisada.",
                List.of(
                        new RenderizacaoAtaDocxService.ParticipanteAta(
                                "Pessoa Presente", "Titular", true, false, null),
                        new RenderizacaoAtaDocxService.ParticipanteAta(
                                "Pessoa Ausente", "Suplente", false, true, "Férias")));
    }
}
