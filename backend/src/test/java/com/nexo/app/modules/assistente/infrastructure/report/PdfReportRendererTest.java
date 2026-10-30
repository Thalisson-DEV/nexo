package com.nexo.app.modules.assistente.infrastructure.report;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PdfReportRendererTest {

    private final PdfReportRenderer renderer = new PdfReportRenderer();

    @Test
    void geraPdfComCapaKpisGraficosTabelaPaginadaEProcedencia() throws Exception {
        List<List<Object>> linhas = new ArrayList<>();
        for (int i = 1; i <= 120; i++) linhas.add(List.of("Material " + i, i * 3, i * 12.5));
        Map<String, Object> tabela = Map.of("tipo", "tabela", "titulo", "Detalhamento", "resultado", "r1", "procedencia", "INTERNO",
                "colunas", List.of(Map.of("nome", "material", "rotulo", "Material", "tipo", "texto"),
                        Map.of("nome", "quantidade", "rotulo", "Quantidade", "tipo", "inteiro"),
                        Map.of("nome", "valor_total", "rotulo", "Valor total", "tipo", "numero")),
                "linhas", linhas, "total", 120, "exibidas", 120);
        List<Map<String, Object>> componentes = List.of(
                Map.of("tipo", "kpi", "titulo", "Saídas no mês", "valor", 1532, "formato", "inteiro",
                        "comparacao", Map.of("valor", 1300, "variacao", 0.178, "rotulo", "vs. agosto")),
                Map.of("tipo", "kpi", "titulo", "Valor movimentado", "valor", 48750.9, "formato", "moeda"),
                Map.of("tipo", "grafico", "grafico", "barra", "titulo", "Saídas por obra", "resultado", "r1", "procedencia", "INTERNO",
                        "rotulos", List.of("Obra A", "Obra B", "Obra C"),
                        "series", List.of(Map.of("nome", "Quantidade", "valores", List.of(120, 80, 45))), "formato", "inteiro"),
                Map.of("tipo", "grafico", "grafico", "linha", "titulo", "Tendência diária", "resultado", "r1", "procedencia", "INTERNO",
                        "rotulos", List.of("01/09", "02/09", "03/09", "04/09"),
                        "series", List.of(Map.of("nome", "Saídas", "valores", List.of(10, 25, 18, 30))), "formato", "inteiro"),
                Map.of("tipo", "grafico", "grafico", "rosca", "titulo", "Composição", "resultado", "r1", "procedencia", "INTERNO",
                        "rotulos", List.of("Cabo", "Poste", "Outros"),
                        "series", List.of(Map.of("nome", "Quantidade", "valores", List.of(50, 30, 20))), "formato", "inteiro"),
                Map.of("tipo", "grafico", "grafico", "barra_horizontal", "titulo", "Ranking", "resultado", "r1", "procedencia", "INTERNO",
                        "rotulos", List.of("Equipe Norte", "Equipe Sul"),
                        "series", List.of(Map.of("nome", "Desvios", "valores", List.of(7, 3))), "formato", "inteiro"),
                tabela);

        byte[] pdf = renderer.renderizar(new PdfReportRenderer.Relatorio("Consumo de materiais — setembro/2026",
                "Almoxarifado central", "01/09/2026 a 25/09/2026", "Ana Souza", OffsetDateTime.now(),
                "O consumo cresceu **17,8%** em relação a agosto, puxado pela Obra A.\n- Destaque 1\n- Destaque 2 → ação",
                List.of(new PdfReportRenderer.Secao("Indicadores e tendências", "Texto de análise com ≥ 3 pontos e emoji 🚧.",
                        componentes)),
                "1. Revisar reservas.\n2. Antecipar compra de cabo.",
                List.of(Map.of("codigo", "r1", "titulo", "Saídas de setembro", "natureza", "INTERNO", "origem",
                        "consultar_movimentacoes", "linhas", 120, "consulta", Map.of("inicio", "2026-09-01")))));

        assertThat(pdf).isNotEmpty();
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            assertThat(documento.getNumberOfPages()).isGreaterThanOrEqualTo(3);
            String texto = new PDFTextStripper().getText(documento);
            assertThat(texto).contains("Consumo de materiais", "Resumo executivo", "Saídas por obra", "Procedência dos dados",
                    "Página 1 de", "R$ 48.750,90", "Material 120", "Conclusões e recomendações");
            assertThat(texto).doesNotContain("**");
            assertThat(documento.getDocumentInformation().getTitle()).startsWith("Consumo de materiais");
        }
    }

    @Test
    void formataNumerosNoPadraoBrasileiro() {
        assertThat(PdfReportRenderer.formatar(1234.5, "moeda", false)).isEqualTo("R$ 1.234,50");
        assertThat(PdfReportRenderer.formatar(0.125, "percentual", false)).isEqualTo("12,5%");
        assertThat(PdfReportRenderer.formatar(2_500_000, "numero", true)).isEqualTo("2,5 mi");
        assertThat(PdfReportRenderer.formatar(null, "numero", false)).isEqualTo("-");
    }

    @Test
    void escalaDosEixosIncluiZeroEPassosRedondos() {
        double[] escala = PdfReportRenderer.escala(3, 87);
        assertThat(escala[0]).isZero();
        assertThat(escala[1]).isGreaterThanOrEqualTo(87);
        assertThat(escala[2]).isIn(10.0, 20.0, 25.0, 50.0);
    }
}
