package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.domain.ItemPlanejado;
import com.nexo.app.modules.obras.domain.KitAplicado;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.OperacaoItemKit;
import com.nexo.app.modules.obras.domain.TipoItemKit;
import com.nexo.app.modules.obras.infrastructure.persistence.ApontamentoExecucaoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.KitAplicadoRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RelatorioMedicaoDetalhadoUseCasesTest {

    private static final UUID ID_OBRA = UUID.randomUUID();
    private static final LocalDate CORTE = LocalDate.of(2026, 10, 31);

    private final KitAplicadoRepository kits = mock(KitAplicadoRepository.class);
    private final ApontamentoExecucaoRepository apontamentos = mock(ApontamentoExecucaoRepository.class);
    private final MedicaoObraUseCases medicoes = mock(MedicaoObraUseCases.class);
    private final RelatorioMedicaoDetalhadoUseCases useCases = new RelatorioMedicaoDetalhadoUseCases(null, kits, null, null, apontamentos,
            medicoes, null, new DocumentoTecnicoPdf());

    /**
     * Orçado e faturado da planilha encontram o planejado e o executado do sistema pelo código e pela operação, mesmo
     * quando a planilha perdeu o zero à esquerda. Faturado depois do corte e de outra obra fica fora.
     */
    @Test
    void juntaPlanilhaESistemaPorCodigoEOperacao() throws IOException {
        Obra obra = obra();
        ItemPlanejado servico = item(TipoItemKit.SERVICO, "0103", OperacaoItemKit.INSTALAR, "10");
        ItemPlanejado extra = item(TipoItemKit.SERVICO, "9999", OperacaoItemKit.INSTALAR, "1");
        ItemPlanejado material = item(TipoItemKit.MATERIAL, "M-1", OperacaoItemKit.INSTALAR, "4");
        KitAplicado kit = mock(KitAplicado.class);
        when(kit.getItensAtivos()).thenReturn(List.of(servico, extra, material));
        when(kits.listarComItens(ID_OBRA)).thenReturn(List.of(kit));
        List<ApontamentoExecucaoRepository.ExecucaoDoItem> execucao = List.of(
                new ApontamentoExecucaoRepository.ExecucaoDoItem(servico.getId(), new BigDecimal("8"), new BigDecimal("800"), CORTE),
                new ApontamentoExecucaoRepository.ExecucaoDoItem(extra.getId(), BigDecimal.ONE, new BigDecimal("50"), CORTE),
                new ApontamentoExecucaoRepository.ExecucaoDoItem(material.getId(), new BigDecimal("3"), null, CORTE));
        when(apontamentos.somarPorItemDaObraAte(ID_OBRA, CORTE)).thenReturn(execucao);
        when(medicoes.resumosDaObra(any())).thenReturn(List.of());

        PlanilhaOrcadoFaturado.Conteudo conteudo = new PlanilhaOrcadoFaturado.Conteudo(
                List.of(new PlanilhaOrcadoFaturado.LinhaOrcada(2, "7000001", OperacaoItemKit.INSTALAR, "M-1", "Poste", "UN", new BigDecimal("4"), null, new BigDecimal("400"), null, null)),
                List.of(new PlanilhaOrcadoFaturado.LinhaOrcada(2, "7000001", OperacaoItemKit.INSTALAR, "103", "Instalar poste", null, new BigDecimal("10"), null, new BigDecimal("1000"), null, null),
                        new PlanilhaOrcadoFaturado.LinhaOrcada(3, "7000001", OperacaoItemKit.INSTALAR, "103", "Instalar poste", null, new BigDecimal("2"), null, new BigDecimal("200"), null, null),
                        new PlanilhaOrcadoFaturado.LinhaOrcada(4, "7000002", OperacaoItemKit.INSTALAR, "103", "Outra obra", null, BigDecimal.ONE, null, BigDecimal.TEN, null, null)),
                List.of(faturado(2, CORTE.minusDays(10), "500", "A", false),
                        faturado(3, CORTE.minusDays(5), "100", "A", true),
                        faturado(4, CORTE.minusDays(2), "300", "P", false),
                        faturado(5, CORTE.plusDays(1), "999", "A", false)),
                List.of());

        RelatorioMedicaoDetalhado r = useCases.montar(obra, "Cidade", conteudo, CORTE, "Fiscal Fictício", null);

        assertThat(r.maoDeObra()).hasSize(2);
        RelatorioMedicaoDetalhado.Linha poste = r.maoDeObra().getFirst();
        assertThat(poste.codigo()).isEqualTo("0103");
        assertThat(poste.orcadoValor()).isEqualByComparingTo("1200");
        assertThat(poste.executadoValor()).isEqualByComparingTo("800");
        assertThat(poste.faturadoValor()).isEqualByComparingTo("400");
        assertThat(poste.aFaturar()).isEqualByComparingTo("400");
        assertThat(poste.acimaDoOrcado()).isFalse();
        assertThat(r.maoDeObra().get(1).acimaDoOrcado()).isTrue();
        assertThat(r.materiais().getFirst().executadoQuantidade()).isEqualByComparingTo("3");
        assertThat(r.materiais().getFirst().executadoValor()).isNull();
        assertThat(r.faturado()).hasSize(3);
        assertThat(r.paraConferir()).hasSize(2);
        assertThat(r.executado()).isEqualByComparingTo("850");
        assertThat(r.aFaturar()).isEqualByComparingTo("450");
        assertThat(r.saldoOrcado()).isEqualByComparingTo("800");

        try (PDDocument pdf = Loader.loadPDF(new DocumentoTecnicoPdf().relatorioMedicao(r, Map.of()))) {
            String texto = new PDFTextStripper().getText(pdf);
            assertThat(texto).contains("Relatório de medição detalhado", "Obra 7000001", "31/10/2026", "Fiscal Fictício",
                    "1.200,00", "Faturado para conferir", "* Executado acima do orçado");
        }
        assertThat(useCases.planilha(r)).isNotEmpty();
    }

    private static PlanilhaOrcadoFaturado.LinhaFaturada faturado(int linha, LocalDate data, String valor, String situacao, boolean credito) {
        return new PlanilhaOrcadoFaturado.LinhaFaturada(linha, "7000001", data, OperacaoItemKit.INSTALAR, "103", "Instalar poste",
                new BigDecimal("4"), new BigDecimal(valor), situacao, credito);
    }

    private static Obra obra() {
        Obra obra = mock(Obra.class);
        when(obra.getId()).thenReturn(ID_OBRA);
        when(obra.getNumero()).thenReturn("7000001");
        when(obra.getTitulo()).thenReturn("Obra de teste");
        return obra;
    }

    private static ItemPlanejado item(TipoItemKit tipo, String codigo, OperacaoItemKit operacao, String quantidade) {
        ItemPlanejado item = Mockito.mock(ItemPlanejado.class);
        when(item.getId()).thenReturn(UUID.randomUUID());
        when(item.getTipo()).thenReturn(tipo);
        when(item.getCodigo()).thenReturn(codigo);
        when(item.getOperacao()).thenReturn(operacao);
        when(item.getDescricao()).thenReturn("Item " + codigo);
        when(item.getQuantidadePrevista()).thenReturn(new BigDecimal(quantidade));
        return item;
    }
}
