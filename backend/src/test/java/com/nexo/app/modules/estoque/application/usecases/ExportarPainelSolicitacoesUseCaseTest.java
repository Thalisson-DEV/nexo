package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.ExportarPainelSolicitacoesRequest;
import com.nexo.app.modules.estoque.application.dto.ExportarPainelSolicitacoesRequest.Conteudo;
import com.nexo.app.modules.estoque.application.dto.ExportarPainelSolicitacoesRequest.Nivel;
import com.nexo.app.modules.estoque.application.dto.ExportarPainelSolicitacoesRequest.Situacao;
import com.nexo.app.modules.estoque.application.dto.PainelSolicitacoesFilter;
import com.nexo.app.modules.estoque.application.dto.PainelSolicitacoesResponse;
import com.nexo.app.modules.estoque.application.dto.PainelSolicitacoesResponse.Solicitacao;
import com.nexo.app.modules.estoque.infrastructure.persistence.PainelSolicitacoesRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.projections.ItemAnaliticoProjection;
import com.nexo.app.modules.rh.EquipeApi;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExportarPainelSolicitacoesUseCaseTest {

    @Mock private PainelSolicitacoesUseCase painelUseCase;
    @Mock private PainelSolicitacoesRepository repository;
    @Mock private EquipeApi equipeApi;
    @InjectMocks private ExportarPainelSolicitacoesUseCase useCase;

    private static final OffsetDateTime AGORA = OffsetDateTime.now();
    private static final PainelSolicitacoesFilter FILTRO =
            new PainelSolicitacoesFilter(AGORA.minusDays(10), AGORA, null, null, null, null, null);

    private final Solicitacao atrasada = solicitacao("EQ-01", "PENDENTE_RETIRADA", LocalDate.now().minusDays(3), null);
    private final Solicitacao concluida = solicitacao("EQ-01", "CONCLUIDO", null, AGORA.minusHours(1));
    private final Solicitacao recusada = solicitacao("EQ-02", "REJEITADO", null, null);

    private static Solicitacao solicitacao(String equipe, String etapa, LocalDate prevista, OffsetDateTime entrega) {
        return new Solicitacao(UUID.randomUUID(), "COMERCIAL", "PENDENTE", etapa, null, null, null, null, null, null,
                null, AGORA.minusDays(2), null, entrega, prevista, null, null, null, "Almox", "Destino", UUID.randomUUID(),
                "Almox Central", UUID.randomUUID(), equipe, "Salvador", "Comercial", null, false, null, false, false,
                false, 2, new BigDecimal("10"), new BigDecimal("4"), false);
    }

    private static ItemAnaliticoProjection item(UUID solicitacao, String codigo, String qtd, String atendida) {
        ItemAnaliticoProjection i = mock(ItemAnaliticoProjection.class);
        when(i.getIdSolicitacao()).thenReturn(solicitacao);
        when(i.getCodigo()).thenReturn(codigo);
        when(i.getDescricao()).thenReturn("Material " + codigo);
        when(i.getUnidade()).thenReturn("UN");
        when(i.getQuantidadeSolicitada()).thenReturn(new BigDecimal(qtd));
        when(i.getQuantidadeAtendida()).thenReturn(new BigDecimal(atendida));
        return i;
    }

    @BeforeEach
    void painel() {
        when(painelUseCase.obter(any(), anyBoolean())).thenReturn(new PainelSolicitacoesResponse(AGORA, AGORA.minusDays(10),
                AGORA, 5000, false, List.of(atrasada, concluida, recusada), List.of(), List.of()));
        when(equipeApi.listarEquipesAtivas()).thenReturn(List.of());
        List<ItemAnaliticoProjection> itens = List.of(
                item(atrasada.id(), "M1", "10", "4"),
                item(concluida.id(), "M1", "5", "5"),
                item(concluida.id(), "M2", "2", "2"),
                item(recusada.id(), "M3", "1", "0"));
        when(repository.listarItens(anyList())).thenReturn(itens);
    }

    private static ExportarPainelSolicitacoesRequest pedido(Conteudo c, Nivel n, Situacao s) {
        return new ExportarPainelSolicitacoesRequest(c, n, s, null, "xlsx");
    }

    @Test
    void resumoTrazUmaSolicitacaoPorLinhaComSituacao() {
        var e = useCase.montar(FILTRO, pedido(null, null, null));

        assertThat(e.aba()).isEqualTo("Solicitações");
        assertThat(e.linhas()).hasSize(3);
        assertThat(e.linhas().get(0)[5]).isEqualTo("Atrasada");
        assertThat(e.linhas().get(1)[5]).isEqualTo("Concluída");
        assertThat(e.procedencia()).containsEntry("Situação", "Todas").containsEntry("Linhas exportadas", "3");
        verify(painelUseCase).obter(FILTRO, true);
    }

    @Test
    void filtraPelaSituacaoEPeloBacklog() {
        var e = useCase.montar(FILTRO, new ExportarPainelSolicitacoesRequest(null, null, Situacao.ATRASADA, false, null));

        assertThat(e.linhas()).hasSize(1);
        verify(painelUseCase).obter(FILTRO, false);
    }

    @Test
    void detalhadoTrazUmItemPorLinha() {
        var e = useCase.montar(FILTRO, pedido(Conteudo.SOLICITACOES, Nivel.DETALHADO, null));

        assertThat(e.aba()).isEqualTo("Itens das solicitações");
        assertThat(e.linhas()).hasSize(4);
        // Qtd. pendente do item atrasado: 10 - 4
        assertThat((BigDecimal) e.linhas().get(0)[18]).isEqualByComparingTo("6");
    }

    @Test
    void materiaisSomamPorCodigoSemAsRecusadas() {
        var e = useCase.montar(FILTRO, pedido(Conteudo.MATERIAIS, Nivel.RESUMO, null));

        assertThat(e.linhas()).hasSize(2);
        Object[] m1 = e.linhas().get(0);
        assertThat(m1[0]).isEqualTo("M1");
        assertThat(m1[3]).isEqualTo(2);
        assertThat((BigDecimal) m1[4]).isEqualByComparingTo("15");
        assertThat((BigDecimal) m1[5]).isEqualByComparingTo("9");
    }

    @Test
    void equipesConsolidamFilaEConclusao() {
        var e = useCase.montar(FILTRO, pedido(Conteudo.EQUIPES, null, null));

        assertThat(e.cabecalho()[0]).isEqualTo("Equipe");
        Object[] eq01 = e.linhas().get(0);
        assertThat(eq01[0]).isEqualTo("EQ-01");
        assertThat(eq01[3]).isEqualTo(2L); // solicitações
        assertThat(eq01[6]).isEqualTo(1L); // atrasadas
    }

    @Test
    void escreveXlsxComAbaSobreECsvComCabecalho() throws Exception {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ExportarPainelSolicitacoesUseCase.escreverXlsx(useCase.montar(FILTRO, pedido(null, null, null)), saida);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(saida.toByteArray()))) {
            assertThat(wb.getSheetName(0)).isEqualTo("Solicitações");
            assertThat(wb.getSheetAt(0).getLastRowNum()).isEqualTo(3);
            assertThat(wb.getSheet("Sobre")).isNotNull();
        }

        StringWriter csv = new StringWriter();
        ExportarPainelSolicitacoesUseCase.escreverCsv(
                useCase.montar(FILTRO, new ExportarPainelSolicitacoesRequest(Conteudo.ALMOXARIFADOS, null, null, null, "csv")), csv);
        assertThat(csv.toString()).startsWith("Almoxarifado;Solicitações;");
        assertThat(ExportarPainelSolicitacoesUseCase.nomeArquivo(FILTRO, pedido(Conteudo.ALMOXARIFADOS, null, null)))
                .startsWith("solicitacoes_geral_almoxarifados_resumo_");
        verify(repository, org.mockito.Mockito.never()).listarItens(eq(List.of()));
    }
}
