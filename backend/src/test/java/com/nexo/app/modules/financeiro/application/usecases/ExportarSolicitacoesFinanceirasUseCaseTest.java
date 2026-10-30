package com.nexo.app.modules.financeiro.application.usecases;

import com.nexo.app.modules.financeiro.application.dto.AcoesDocumentoResponse;
import com.nexo.app.modules.financeiro.application.dto.CampoValorResponse;
import com.nexo.app.modules.financeiro.application.dto.DesfechoResponse;
import com.nexo.app.modules.financeiro.application.dto.DocumentoResumoResponse;
import com.nexo.app.modules.financeiro.application.dto.FormularioResumoResponse;
import com.nexo.app.modules.financeiro.application.dto.FornecedorSolicitacaoResponse;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoCompraResponse;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoCompraResumoResponse;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoPagamentoResponse;
import com.nexo.app.modules.financeiro.domain.SituacaoDocumento;
import com.nexo.app.modules.financeiro.domain.StatusSolicitacao;
import com.nexo.app.modules.financeiro.domain.TipoDocumentoFinanceiro;
import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.application.dto.Paged;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportarSolicitacoesFinanceirasUseCaseTest {

    private static final OffsetDateTime DATA = OffsetDateTime.parse("2026-08-31T14:30:45-03:00");

    @Mock
    private SolicitacaoCompraUseCases compraUseCases;
    @Mock
    private SolicitacaoPagamentoUseCases pagamentoUseCases;
    @Mock
    private HistoricoSolicitacaoFinanceira historico;

    private ExportarSolicitacoesFinanceirasUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ExportarSolicitacoesFinanceirasUseCase(compraUseCases, pagamentoUseCases, historico);
    }

    @Test
    void exportaOsQuatroFormatosComCamposAusentesECompraVinculada() throws Exception {
        SolicitacaoCompraResponse compra = compra(1042L, Map.of(), List.of(), null);
        SolicitacaoPagamentoResponse pagamento = pagamento(507L, Map.of(), List.of(), compraVinculada());
        when(compraUseCases.listar(any(), any(Pageable.class))).thenReturn(pagina(List.of(compra)));
        when(pagamentoUseCases.listar(any(), any(Pageable.class))).thenReturn(pagina(List.of(pagamento)));

        CSVRecord compraResumo = unica(exportar(writer -> useCase.exportarComprasResumo(null, writer)));
        CSVRecord compraDetalhada = unica(exportar(writer -> useCase.exportarComprasDetalhado(null, writer)));
        CSVRecord pagamentoResumo = unica(exportar(writer -> useCase.exportarPagamentosResumo(null, writer)));
        CSVRecord pagamentoDetalhado = unica(exportar(writer -> useCase.exportarPagamentosDetalhado(null, writer)));

        assertThat(compraResumo.get("Número")).isEqualTo("1042");
        assertThat(compraResumo.get("Valor Aprovado")).isEmpty();
        assertThat(compraResumo.get("Data Decisão")).isEmpty();
        assertThat(compraDetalhada.isMapped("Campo - Placa")).isTrue();
        assertThat(compraDetalhada.get("Quantidade Documentos")).isEqualTo("0");
        assertThat(pagamentoResumo.get("Compra Vinculada Número")).isEqualTo("1042");
        assertThat(pagamentoDetalhado.get("Compra Vinculada Status")).isEqualTo("APROVADO");
        assertThat(pagamentoDetalhado.get("Compra Vinculada Fornecedor")).isEqualTo("Fornecedor origem");
        assertThat(pagamentoDetalhado.isMapped("Campo - Tipo de Despesa")).isTrue();
        assertThat(pagamentoDetalhado.isMapped("Campo - Placa")).isFalse();
    }

    @Test
    void detalhadoEscapaCsvNeutralizaFormulasEIncluiDocumentos() throws Exception {
        Map<String, CampoValorResponse> valores = Map.of(
                "descricao", CampoValorResponse.de("=2+2", "=2+2"),
                "observacoes", CampoValorResponse.de("linha 1;linha 2\nlinha 3", "linha 1;linha 2\nlinha 3"));
        DocumentoResumoResponse documento = documento("nota;fiscal.csv");
        SolicitacaoCompraResponse compra = compra(1042L, valores, List.of(documento), desfecho());
        when(compraUseCases.listar(any(), any(Pageable.class))).thenReturn(pagina(List.of(compra)));

        String csv = exportar(writer -> useCase.exportarComprasDetalhado(null, writer));
        CSVRecord registro = unica(csv);

        assertThat(registro.get("Usuário Solicitante")).isEqualTo("'=usuario-perigoso");
        assertThat(registro.get("Campo - Descrição da Compra / Serviço")).isEqualTo("'=2+2");
        assertThat(registro.get("Campo - Observações, OS ou Rateio"))
                .isEqualTo("linha 1;linha 2\nlinha 3");
        assertThat(registro.get("Quantidade Documentos")).isEqualTo("1");
        assertThat(registro.get("Documentos"))
                .contains("nome=nota;fiscal.csv", "tipo=Nota fiscal", "situação=Conferido",
                        "número=NF-123", "valor=99.90", "duplicado=Não", "pendente=Não");
        assertThat(csv).contains("\"linha 1;linha 2");
    }

    @Test
    void detalhadoMostraQuantasVezesADecisaoFoiDesfeita() throws Exception {
        SolicitacaoCompraResponse compra = compra(1042L, Map.of(), List.of(), null);
        when(compraUseCases.listar(any(), any(Pageable.class))).thenReturn(pagina(List.of(compra)));
        when(historico.decisoesDesfeitas(List.of(compra.id()))).thenReturn(Map.of(compra.id(),
                new HistoricoSolicitacaoFinanceira.DecisoesDesfeitas(2, DATA, "Diretora", "Valor digitado errado")));

        CSVRecord registro = unica(exportar(writer -> useCase.exportarComprasDetalhado(null, writer)));

        assertThat(registro.get("Decisões Desfeitas")).isEqualTo("2");
        assertThat(registro.get("Última Decisão Desfeita Em")).isEqualTo("31/08/2026 14:30:45");
        assertThat(registro.get("Última Decisão Desfeita Por")).isEqualTo("Diretora");
        assertThat(registro.get("Motivo Última Decisão Desfeita")).isEqualTo("Valor digitado errado");
    }

    @Test
    void paginaEmLotesDeQuinhentosComOrdenacaoTotalECortaEmDezMil() throws Exception {
        SolicitacaoCompraResponse compra = compra(1L, Map.of(), List.of(), null);
        List<SolicitacaoCompraResponse> lote = Collections.nCopies(500, compra);
        when(compraUseCases.listar(any(), any(Pageable.class))).thenAnswer(invocacao -> {
            Pageable pageable = invocacao.getArgument(1);
            return new PageResponse<>(lote, new Paged(500, pageable.getPageNumber(), 10_500, 21));
        });

        String csv = exportar(writer -> useCase.exportarComprasResumo(null, writer));
        try (CSVParser parser = parser(csv)) {
            assertThat(parser.getRecords()).hasSize(10_000);
        }

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(compraUseCases, times(20)).listar(any(), captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(pageable -> {
            assertThat(pageable.getPageSize()).isEqualTo(500);
            assertThat(pageable.getSort().getOrderFor("dataCriacao").isDescending()).isTrue();
            assertThat(pageable.getSort().getOrderFor("id").isDescending()).isTrue();
        });
        assertThat(captor.getAllValues()).extracting(Pageable::getPageNumber)
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, 20).boxed().toList());
    }

    @Test
    void resultadoVazioGeraSomenteCabecalho() throws Exception {
        when(pagamentoUseCases.listar(any(), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(), new Paged(500, 0, 0, 0)));

        String csv = exportar(writer -> useCase.exportarPagamentosResumo(null, writer));

        try (CSVParser parser = parser(csv)) {
            assertThat(parser.getHeaderNames()).contains("Número", "Compra Vinculada ID");
            assertThat(parser.getRecords()).isEmpty();
        }
        verify(pagamentoUseCases).listar(any(), any(Pageable.class));
    }

    private SolicitacaoCompraResponse compra(Long numero, Map<String, CampoValorResponse> valores,
                                             List<DocumentoResumoResponse> documentos,
                                             DesfechoResponse desfecho) {
        return new SolicitacaoCompraResponse(
                UUID.randomUUID(), numero, StatusSolicitacao.SOLICITADO,
                formulario(TipoSolicitacao.COMPRA), valores, new BigDecimal("150.75"), fornecedor(),
                UUID.randomUUID(), "=usuario-perigoso", "Maria Solicitante", DATA,
                desfecho, documentos, null, true, DATA.minusDays(1), DATA, "auditor");
    }

    private SolicitacaoPagamentoResponse pagamento(Long numero, Map<String, CampoValorResponse> valores,
                                                   List<DocumentoResumoResponse> documentos,
                                                   SolicitacaoCompraResumoResponse compra) {
        return new SolicitacaoPagamentoResponse(
                UUID.randomUUID(), numero, StatusSolicitacao.APROVADO,
                formulario(TipoSolicitacao.PAGAMENTO), valores, new BigDecimal("100.50"), fornecedor(), compra,
                UUID.randomUUID(), "usuario", "Maria Solicitante", DATA,
                desfecho(), documentos, null, true, DATA.minusDays(1), DATA, "auditor");
    }

    private FormularioResumoResponse formulario(TipoSolicitacao tipo) {
        return new FormularioResumoResponse(
                UUID.randomUUID(), tipo, "PADRAO", "Formulário padrão", "Descrição", true, true);
    }

    private FornecedorSolicitacaoResponse fornecedor() {
        return new FornecedorSolicitacaoResponse(
                UUID.randomUUID(), "Fornecedor principal", "Fornecedor Principal Ltda", true);
    }

    private DesfechoResponse desfecho() {
        return new DesfechoResponse(
                UUID.randomUUID(), "diretor", DATA, new BigDecimal("100.50"),
                "Aprovado", null, null, null, null);
    }

    private SolicitacaoCompraResumoResponse compraVinculada() {
        return new SolicitacaoCompraResumoResponse(
                UUID.randomUUID(), 1042L, StatusSolicitacao.APROVADO,
                new BigDecimal("100.50"), "Fornecedor origem", "Compra original");
    }

    private DocumentoResumoResponse documento(String nomeArquivo) {
        return new DocumentoResumoResponse(
                UUID.randomUUID(), nomeArquivo, "application/pdf", 100L,
                TipoDocumentoFinanceiro.NOTA_FISCAL, "Nota fiscal",
                SituacaoDocumento.CONFERIDO, "Conferido", "NF-123", null, null,
                false, false, false, new BigDecimal("99.90"), UUID.randomUUID(), DATA,
                "Documento válido", null, (AcoesDocumentoResponse) null, DATA, "usuario");
    }

    private <T> PageResponse<T> pagina(List<T> conteudo) {
        return new PageResponse<>(conteudo, new Paged(500, 0, conteudo.size(), 1));
    }

    private String exportar(Exportacao exportacao) throws Exception {
        StringWriter writer = new StringWriter();
        exportacao.executar(writer);
        return writer.toString();
    }

    private CSVRecord unica(String csv) throws Exception {
        try (CSVParser parser = parser(csv)) {
            List<CSVRecord> registros = new ArrayList<>(parser.getRecords());
            assertThat(registros).hasSize(1);
            return registros.getFirst();
        }
    }

    private CSVParser parser(String csv) throws Exception {
        return CSVParser.parse(csv, CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setDelimiter(';')
                .build());
    }

    @FunctionalInterface
    private interface Exportacao {
        void executar(StringWriter writer) throws Exception;
    }
}
