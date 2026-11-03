package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.domain.EquipamentoObra;
import com.nexo.app.modules.obras.domain.EstruturaChecklist;
import com.nexo.app.modules.obras.domain.FichaAsBuilt;
import com.nexo.app.modules.obras.domain.ModeloChecklistObra;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.OrigemPlanejamento;
import com.nexo.app.modules.obras.domain.PontoPlanejamento;
import com.nexo.app.modules.obras.domain.RespostasChecklist;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentoTecnicoPdfTest {

    private static final UUID ID_OBRA = UUID.randomUUID();

    private static byte[] projeto(int paginas) throws IOException {
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            for (int i = 0; i < paginas; i++) {
                documento.addPage(new PDPage(PDRectangle.A3));
            }
            documento.save(saida);
            return saida.toByteArray();
        }
    }

    @Test
    void asBuiltLevaSoAsFolhasExecutadasEAsFichasMarcandoNaoSeAplica() throws IOException {
        PontoPlanejamento ponto = new PontoPlanejamento(ID_OBRA, "D381272", 2, new BigDecimal("0.5"), new BigDecimal("0.5"), OrigemPlanejamento.MANUAL);
        UUID idPonto = UUID.randomUUID();
        ReflectionTestUtils.setField(ponto, "id", idPonto);
        EstruturaChecklist estrutura = new EstruturaChecklist(List.of(new EstruturaChecklist.Grupo("Estrutura", List.of(
                new EstruturaChecklist.Pergunta("p1", "Aterramento", EstruturaChecklist.TipoPergunta.TEXTO, true, true, null)))));
        ModeloChecklistObra modelo = new ModeloChecklistObra(ModeloChecklistObra.Finalidade.AS_BUILT, "Ficha", null, estrutura);
        FichaAsBuilt ficha = new FichaAsBuilt(ID_OBRA, idPonto, modelo,
                RespostasChecklist.de(estrutura, List.of(new RespostasChecklist.Informada("p1", null, List.of(), true))));
        byte[] foto = FotoEvidencia.jpeg(new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB), FotoEvidencia.LADO_DOCUMENTO, 0.78f);
        Obra obra = Mockito.mock(Obra.class);
        Mockito.when(obra.getNumero()).thenReturn("7000001");
        Mockito.when(obra.getTitulo()).thenReturn("Obra de teste");

        byte[] pdf = new DocumentoTecnicoPdf().asBuilt(new DocumentoTecnicoPdf.DadosAsBuilt(obra, 2, projeto(3), List.of(ponto), Set.of(idPonto),
                List.of(), Set.of(), List.of(ficha), List.of(new EquipamentoObra(ID_OBRA, idPonto, EquipamentoObra.Tipo.RELIGADOR, true, null)),
                Map.of(idPonto, List.of(new DocumentoTecnicoPdf.Foto("Aterramento", "Vista da haste", "01/11/2026 10:00 · ANA", foto), new DocumentoTecnicoPdf.Foto("Outras evidências", null, "01/11/2026 10:05", foto)))));

        try (PDDocument gerado = Loader.loadPDF(pdf)) {
            assertThat(gerado.getNumberOfPages()).isEqualTo(2);
            String texto = new PDFTextStripper().getText(gerado);
            assertThat(texto).contains("D381272", "Não se aplica", "Versão", "Religador com trafo auxiliar", "Aterramento · Vista da haste", "01/11/2026 10:00 · ANA", "Outras evidências");
            assertThat(gerado.getPage(1).getResources().getXObjectNames()).hasSize(2);
            assertThat(gerado.getPage(1).getResources().getXObject(gerado.getPage(1).getResources().getXObjectNames().iterator().next())).isInstanceOf(PDImageXObject.class);
        }
    }
}
