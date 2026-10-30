package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.DevolucaoComercialRequest;
import com.nexo.app.modules.estoque.application.dto.MovimentacaoBulkRequest;
import com.nexo.app.modules.estoque.application.dto.MovimentacaoEstoqueFilter;
import com.nexo.app.modules.estoque.application.dto.MovimentacaoItemResponse;
import com.nexo.app.modules.estoque.application.dto.MovimentacaoOperacaoResponse;
import com.nexo.app.modules.estoque.application.mappers.EstoqueMapper;
import com.nexo.app.modules.estoque.application.mappers.MovimentacaoEstoqueMapper;
import com.nexo.app.modules.estoque.domain.*;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MovimentacaoEstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoSerieRepository;
import com.nexo.app.modules.estoque.application.services.AssinaturaTransferenciaService;
import com.nexo.app.modules.estoque.application.services.DebitoSaldoService;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MovimentacaoEstoqueUseCasesTest {

    @Mock
    private MovimentacaoEstoqueRepository repository;
    @Mock
    private EstoqueRepository estoqueRepository;
    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private SaldoRepository saldoRepository;
    @Mock
    private MovimentacaoEstoqueMapper mapper;
    @Mock
    private EstoqueMapper estoqueMapper;
    @Mock
    private UsuarioApi usuarioApi;
    @Mock
    private SaldoSerieRepository serieRepository;
    @Mock
    private DebitoSaldoService debitoSaldoService;
    @Mock
    private EstoqueCampoEquipeProvisioningService estoqueCampoEquipeService;
    @Mock
    private AssinaturaTransferenciaService assinaturaTransferenciaService;

    @InjectMocks
    private MovimentacaoEstoqueUseCases useCases;

    @Test
    void listar_DeveRemoverSortDoPageableAoChamarRepositorio() {
        // Arrange
        MovimentacaoEstoqueFilter filter = new MovimentacaoEstoqueFilter(null, null, null, null, null, null, null, null, null, null, null, null);
        Pageable pageableWithSort = PageRequest.of(0, 10, Sort.by("dataHora").descending());
        
        when(repository.findUniqueOperacoesIds(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        // Act
        useCases.listar(filter, pageableWithSort);

        // Assert
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findUniqueOperacoesIds(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), pageableCaptor.capture());
        
        Pageable capturedPageable = pageableCaptor.getValue();
        assertFalse(capturedPageable.getSort().isSorted(), "O sort deve ser removido ao chamar o repositório");
        assertEquals(0, capturedPageable.getPageNumber());
        assertEquals(10, capturedPageable.getPageSize());
    }

    @Test
    void listar_DeveCombinarTodosOsFiltrosEManterPaginacaoDeterministica() {
        UUID idEstoque = UUID.randomUUID();
        UUID idMaterial = UUID.randomUUID();
        UUID idUsuario = UUID.randomUUID();
        UUID idBaseOrigem = UUID.randomUUID();
        UUID idBaseDestino = UUID.randomUUID();
        UUID idSolicitacao = UUID.randomUUID();
        UUID idObra = UUID.randomUUID();
        UUID idEquipe = UUID.randomUUID();
        UUID idReserva = UUID.randomUUID();
        UUID idAgente = UUID.randomUUID();
        OffsetDateTime dataInicio = OffsetDateTime.parse("2026-08-05T10:00:00Z");
        OffsetDateTime dataFim = OffsetDateTime.parse("2026-08-20T18:00:00Z");
        MovimentacaoEstoqueFilter filter = new MovimentacaoEstoqueFilter(
                idEstoque, idBaseOrigem, idBaseDestino, idMaterial, CondicaoMaterial.NOVO,
                MovimentacaoEstoque.TipoMovimentacao.ENTREGA, dataInicio, dataFim, "AGO/2026",
                idUsuario, " SERIE-1 ", ContextoSolicitacaoMaterial.TECNICO,
                idSolicitacao, TipoSolicitacaoTecnica.ADICAO_COMUM, TipoObraSnapshot.CORRETIVA,
                idObra, " oc   1042 ", idEquipe, idReserva, "000123", idAgente,
                SolicitacaoMaterial.StatusSolicitacao.ENTREGUE_PARCIAL, StatusSeparacao.SEPARADO,
                "  material  ");
        Pageable pageable = PageRequest.of(2, 7, Sort.by("tipo").ascending());
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        when(repository.findUniqueOperacoesIds(
                eq(idEstoque), eq(idMaterial), eq("NOVO"), eq("ENTREGA"),
                eq(dataInicio), eq(dataFim),
                eq(OffsetDateTime.parse("2026-08-01T00:00:00Z")),
                eq(OffsetDateTime.parse("2026-08-31T23:59:59.999Z")),
                eq(idUsuario), eq("%serie-1%"), eq(idBaseOrigem), eq(idBaseDestino),
                eq("%material%"), eq("TECNICO"), eq(idSolicitacao),
                eq("221"), eq("CORRETIVA"), eq(idObra), eq("OC 1042"), eq(idEquipe),
                eq(idReserva), eq("000123"), eq(idAgente), eq("ENTREGUE_PARCIAL"),
                eq("SEPARADO"), eq("tipo"), eq("ASC"), any(Pageable.class)))
                .thenReturn(Page.empty());

        useCases.listar(filter, pageable);

        verify(repository).findUniqueOperacoesIds(
                eq(idEstoque), eq(idMaterial), eq("NOVO"), eq("ENTREGA"),
                eq(dataInicio), eq(dataFim),
                eq(OffsetDateTime.parse("2026-08-01T00:00:00Z")),
                eq(OffsetDateTime.parse("2026-08-31T23:59:59.999Z")),
                eq(idUsuario), eq("%serie-1%"), eq(idBaseOrigem), eq(idBaseDestino),
                eq("%material%"), eq("TECNICO"), eq(idSolicitacao),
                eq("221"), eq("CORRETIVA"), eq(idObra), eq("OC 1042"), eq(idEquipe),
                eq(idReserva), eq("000123"), eq(idAgente), eq("ENTREGUE_PARCIAL"),
                eq("SEPARADO"), eq("tipo"), eq("ASC"), pageableCaptor.capture());
        assertAll(
                () -> assertEquals(2, pageableCaptor.getValue().getPageNumber()),
                () -> assertEquals(7, pageableCaptor.getValue().getPageSize()),
                () -> assertTrue(pageableCaptor.getValue().getSort().isUnsorted()));
    }

    @Test
    void buscarOperacaoTecnica_DeveProjetarContextoEReferenciasImutaveis() {
        UUID idReferencia = UUID.randomUUID();
        UUID idSolicitacao = UUID.randomUUID();
        UUID idReserva = UUID.randomUUID();
        UUID idOperacao = UUID.randomUUID();
        UUID idObra = UUID.randomUUID();
        UUID idEquipe = UUID.randomUUID();
        UUID idOperador = UUID.randomUUID();
        UUID idItemOperacao = UUID.randomUUID();
        UUID idItemSolicitacao = UUID.randomUUID();
        OffsetDateTime realizadaEm = OffsetDateTime.parse("2026-09-01T10:15:00-03:00");

        MovimentacaoEstoque base = mock(MovimentacaoEstoque.class);
        SolicitacaoMaterial solicitacao = mock(SolicitacaoMaterial.class);
        ReservaSolicitacaoTecnica reserva = mock(ReservaSolicitacaoTecnica.class);
        OperacaoSolicitacaoMaterial operacao = mock(OperacaoSolicitacaoMaterial.class);
        MovimentacaoItemResponse item = new MovimentacaoItemResponse(
                UUID.randomUUID(), null, CondicaoMaterial.NOVO, BigDecimal.ONE, null,
                idItemOperacao, idItemSolicitacao);

        when(repository.findAllByIdReferenciaOperacaoInOrderByDataHoraDesc(List.of(idReferencia)))
                .thenReturn(List.of(base));
        when(base.getIdReferenciaOperacao()).thenReturn(idReferencia);
        when(base.getTipo()).thenReturn(MovimentacaoEstoque.TipoMovimentacao.ESTORNO);
        when(base.getDataHora()).thenReturn(realizadaEm);
        when(base.getIdUsuarioOperador()).thenReturn(idOperador);
        when(base.getSolicitacao()).thenReturn(solicitacao);
        when(solicitacao.getId()).thenReturn(idSolicitacao);
        when(solicitacao.getContexto()).thenReturn(ContextoSolicitacaoMaterial.TECNICO);
        when(base.getTipoTecnico()).thenReturn(TipoSolicitacaoTecnica.ADICAO_COMUM);
        when(base.getTipoObraSnapshot()).thenReturn(TipoObraSnapshot.CORRETIVA);
        when(base.getReserva()).thenReturn(reserva);
        when(reserva.getId()).thenReturn(idReserva);
        when(base.getOperacaoSolicitacao()).thenReturn(operacao);
        when(operacao.getId()).thenReturn(idOperacao);
        when(base.getIdObra()).thenReturn(idObra);
        when(base.getIdEquipe()).thenReturn(idEquipe);
        when(usuarioApi.buscarNomePorId(idOperador)).thenReturn(Optional.of("Operador"));
        when(mapper.toItemResponse(base)).thenReturn(item);

        MovimentacaoOperacaoResponse resposta = useCases.buscarOperacaoPorIdReferencia(idReferencia);

        assertAll(
                () -> assertEquals(ContextoSolicitacaoMaterial.TECNICO, resposta.contexto()),
                () -> assertEquals(idSolicitacao, resposta.idSolicitacao()),
                () -> assertEquals(TipoSolicitacaoTecnica.ADICAO_COMUM, resposta.tipoTecnico()),
                () -> assertEquals(TipoObraSnapshot.CORRETIVA, resposta.tipoObra()),
                () -> assertEquals(idReserva, resposta.idReserva()),
                () -> assertEquals(idOperacao, resposta.idOperacao()),
                () -> assertEquals(idObra, resposta.idObra()),
                () -> assertEquals(idEquipe, resposta.idEquipe()),
                () -> assertEquals(idItemOperacao, resposta.itens().getFirst().idItemOperacao()),
                () -> assertEquals(idItemSolicitacao, resposta.itens().getFirst().idItemSolicitacao()));
    }

    @Test
    void devolucaoComercial_DeveProcessarComSucesso_GravandoTipo222EDebitandoEquipe() {
        UUID idEquipe = UUID.randomUUID();
        UUID idEstoqueDestino = UUID.randomUUID();
        UUID idMaterial = UUID.randomUUID();
        UUID idOperador = UUID.randomUUID();

        Estoque origemCampo = mock(Estoque.class);

        TipoEstoque tipoFisico = mock(TipoEstoque.class);
        when(tipoFisico.getNome()).thenReturn("FISICO");
        Estoque destinoFisico = mock(Estoque.class);
        when(destinoFisico.getId()).thenReturn(idEstoqueDestino);
        when(destinoFisico.isAtivo()).thenReturn(true);
        when(destinoFisico.getTipoEstoque()).thenReturn(tipoFisico);

        Material material = mock(Material.class);
        when(material.getId()).thenReturn(idMaterial);
        when(material.isAtivo()).thenReturn(true);
        when(material.isDetalhaSerie()).thenReturn(false);

        when(estoqueCampoEquipeService.resolver(idEquipe)).thenReturn(origemCampo);
        when(estoqueRepository.findById(idEstoqueDestino)).thenReturn(Optional.of(destinoFisico));
        when(materialRepository.findById(idMaterial)).thenReturn(Optional.of(material));
        when(saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(any(), any(), any()))
                .thenReturn(Optional.empty());

        DevolucaoComercialRequest request = new DevolucaoComercialRequest(
                idEquipe,
                idEstoqueDestino,
                List.of(new DevolucaoComercialRequest.ItemDevolucaoComercialRequest(
                        idMaterial, new BigDecimal("5.000"), CondicaoMaterial.SUCATA, null)),
                "Devolução comercial de sucata"
        );

        UUID idReferencia = useCases.devolucaoComercial(request, idOperador);

        assertNotNull(idReferencia);
        verify(debitoSaldoService).debitar(origemCampo, material, CondicaoMaterial.SUCATA, new BigDecimal("5.000"));
        verify(saldoRepository).save(any(Saldo.class));

        ArgumentCaptor<MovimentacaoEstoque> movCaptor = ArgumentCaptor.forClass(MovimentacaoEstoque.class);
        verify(repository).save(movCaptor.capture());
        MovimentacaoEstoque movSalva = movCaptor.getValue();
        assertEquals(TipoSolicitacaoTecnica.DEVOLUCAO_COMUM, movSalva.getTipoTecnico());
        assertEquals("222", movSalva.getTipoTecnico().getCodigo());
        assertEquals(MovimentacaoEstoque.TipoMovimentacao.DEVOLUCAO, movSalva.getTipo());
        assertEquals(CondicaoMaterial.SUCATA, movSalva.getCondicao());
        assertEquals(idEquipe, movSalva.getIdEquipe());
        assertEquals(origemCampo, movSalva.getEstoqueOrigem());
        assertEquals(destinoFisico, movSalva.getEstoqueDestino());
    }

    @Test
    void devolucaoComercial_SemGuardarNoEstoque_DevolveEBaixaNoDestinoSemCreditarSaldo() {
        UUID idEquipe = UUID.randomUUID();
        UUID idEstoqueDestino = UUID.randomUUID();
        UUID idMaterial = UUID.randomUUID();
        UUID idOperador = UUID.randomUUID();

        Estoque origemCampo = mock(Estoque.class);
        Estoque destinoFisico = destinoFisicoAtivo(idEstoqueDestino);
        Material material = mock(Material.class);
        when(material.isDetalhaSerie()).thenReturn(false);

        when(estoqueCampoEquipeService.resolver(idEquipe)).thenReturn(origemCampo);
        when(estoqueRepository.findById(idEstoqueDestino)).thenReturn(Optional.of(destinoFisico));
        when(materialRepository.findById(idMaterial)).thenReturn(Optional.of(material));

        DevolucaoComercialRequest request = new DevolucaoComercialRequest(
                idEquipe,
                idEstoqueDestino,
                List.of(new DevolucaoComercialRequest.ItemDevolucaoComercialRequest(
                        idMaterial, new BigDecimal("3.000"), CondicaoMaterial.SUCATA, null, false)),
                null
        );

        UUID idReferencia = useCases.devolucaoComercial(request, idOperador);

        verify(debitoSaldoService).debitar(origemCampo, material, CondicaoMaterial.SUCATA, new BigDecimal("3.000"));
        verify(saldoRepository, never()).save(any(Saldo.class));

        ArgumentCaptor<MovimentacaoEstoque> movCaptor = ArgumentCaptor.forClass(MovimentacaoEstoque.class);
        verify(repository, times(2)).save(movCaptor.capture());
        MovimentacaoEstoque devolucao = movCaptor.getAllValues().get(0);
        MovimentacaoEstoque baixa = movCaptor.getAllValues().get(1);

        assertEquals(MovimentacaoEstoque.TipoMovimentacao.DEVOLUCAO, devolucao.getTipo());
        assertEquals(idReferencia, devolucao.getIdReferenciaOperacao());
        assertEquals(MovimentacaoEstoque.TipoMovimentacao.BAIXA_AVULSA, baixa.getTipo());
        assertEquals(destinoFisico, baixa.getEstoqueOrigem());
        assertNull(baixa.getEstoqueDestino());
        assertEquals(CondicaoMaterial.SUCATA, baixa.getCondicao());
        assertNotEquals(idReferencia, baixa.getIdReferenciaOperacao());
    }

    @Test
    void devolucaoComercial_SerieSemGuardarNoEstoque_DesativaASerieNoDestino() {
        UUID idEquipe = UUID.randomUUID();
        UUID idEstoqueDestino = UUID.randomUUID();
        UUID idMaterial = UUID.randomUUID();
        UUID idOrigem = UUID.randomUUID();

        Estoque origemCampo = mock(Estoque.class);
        when(origemCampo.getId()).thenReturn(idOrigem);
        Estoque destinoFisico = destinoFisicoAtivo(idEstoqueDestino);
        Material material = mock(Material.class);
        when(material.getId()).thenReturn(idMaterial);
        when(material.isDetalhaSerie()).thenReturn(true);
        SaldoSerie serie = mock(SaldoSerie.class);

        when(estoqueCampoEquipeService.resolver(idEquipe)).thenReturn(origemCampo);
        when(estoqueRepository.findById(idEstoqueDestino)).thenReturn(Optional.of(destinoFisico));
        when(materialRepository.findById(idMaterial)).thenReturn(Optional.of(material));
        when(serieRepository.findByEstoqueIdAndMaterialIdAndNumSerieAndCondicaoAndAtivoTrue(
                idOrigem, idMaterial, "SN-1", CondicaoMaterial.SUCATA)).thenReturn(Optional.of(serie));

        DevolucaoComercialRequest request = new DevolucaoComercialRequest(
                idEquipe,
                idEstoqueDestino,
                List.of(new DevolucaoComercialRequest.ItemDevolucaoComercialRequest(
                        idMaterial, BigDecimal.ONE, CondicaoMaterial.SUCATA, "SN-1", false)),
                null
        );

        useCases.devolucaoComercial(request, UUID.randomUUID());

        verify(serie).transferirPara(destinoFisico);
        verify(serie).desativar();
        verify(serieRepository).save(serie);
        verify(saldoRepository, never()).save(any(Saldo.class));
    }

    @Test
    void devolucaoComercial_DeveRejeitar_SerieRepetida() {
        UUID idEquipe = UUID.randomUUID();
        UUID idEstoqueDestino = UUID.randomUUID();
        UUID idMaterial = UUID.randomUUID();

        when(estoqueCampoEquipeService.resolver(idEquipe)).thenReturn(mock(Estoque.class));
        Estoque destinoFisico = destinoFisicoAtivo(idEstoqueDestino);
        when(estoqueRepository.findById(idEstoqueDestino)).thenReturn(Optional.of(destinoFisico));

        DevolucaoComercialRequest request = new DevolucaoComercialRequest(
                idEquipe,
                idEstoqueDestino,
                List.of(
                        new DevolucaoComercialRequest.ItemDevolucaoComercialRequest(
                                idMaterial, BigDecimal.ONE, CondicaoMaterial.NOVO, "SN-1"),
                        new DevolucaoComercialRequest.ItemDevolucaoComercialRequest(
                                idMaterial, BigDecimal.ONE, CondicaoMaterial.NOVO, "SN-1")),
                null
        );

        InvarianteVioladaException ex = assertThrows(InvarianteVioladaException.class, () ->
                useCases.devolucaoComercial(request, UUID.randomUUID()));
        assertTrue(ex.getMessage().contains("SN-1"));
        verifyNoInteractions(debitoSaldoService);
    }

    private Estoque destinoFisicoAtivo(UUID id) {
        TipoEstoque tipoFisico = mock(TipoEstoque.class);
        when(tipoFisico.getNome()).thenReturn("FISICO");
        Estoque destino = mock(Estoque.class);
        lenient().when(destino.getId()).thenReturn(id);
        when(destino.isAtivo()).thenReturn(true);
        when(destino.getTipoEstoque()).thenReturn(tipoFisico);
        return destino;
    }

    @Test
    void devolucaoComercial_DeveRejeitar_QuandoDestinoNaoForFisico() {
        UUID idEquipe = UUID.randomUUID();
        UUID idEstoqueDestino = UUID.randomUUID();
        UUID idOperador = UUID.randomUUID();

        Estoque origemCampo = mock(Estoque.class);
        when(estoqueCampoEquipeService.resolver(idEquipe)).thenReturn(origemCampo);

        TipoEstoque tipoNaoFisico = mock(TipoEstoque.class);
        when(tipoNaoFisico.getNome()).thenReturn("CAMPO");
        Estoque destinoInvalido = mock(Estoque.class);
        when(destinoInvalido.isAtivo()).thenReturn(true);
        when(destinoInvalido.getTipoEstoque()).thenReturn(tipoNaoFisico);
        when(estoqueRepository.findById(idEstoqueDestino)).thenReturn(Optional.of(destinoInvalido));

        DevolucaoComercialRequest request = new DevolucaoComercialRequest(
                idEquipe,
                idEstoqueDestino,
                List.of(new DevolucaoComercialRequest.ItemDevolucaoComercialRequest(
                        UUID.randomUUID(), BigDecimal.ONE, CondicaoMaterial.NOVO, null)),
                null
        );

        InvarianteVioladaException ex = assertThrows(InvarianteVioladaException.class, () ->
                useCases.devolucaoComercial(request, idOperador));
        assertTrue(ex.getMessage().contains("deve estar ativo e ser do tipo FISICO"));
    }

    @Test
    void transferenciaLote_DeveGravarAssinaturasEArmazenarComSucesso() {
        UUID idOrigem = UUID.randomUUID();
        UUID idDestino = UUID.randomUUID();
        UUID idMaterial = UUID.randomUUID();
        UUID idOperador = UUID.randomUUID();

        Estoque origem = mock(Estoque.class);
        Estoque destino = mock(Estoque.class);
        Material material = mock(Material.class);

        when(destino.isAtivo()).thenReturn(true);
        when(material.isAtivo()).thenReturn(true);
        when(material.isDetalhaSerie()).thenReturn(false);

        when(estoqueRepository.findById(idOrigem)).thenReturn(Optional.of(origem));
        when(estoqueRepository.findById(idDestino)).thenReturn(Optional.of(destino));
        when(materialRepository.findById(idMaterial)).thenReturn(Optional.of(material));
        when(saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(any(), any(), any()))
                .thenReturn(Optional.empty());

        when(assinaturaTransferenciaService.armazenar(any(UUID.class), eq("FISICA"), eq("FISICA")))
                .thenReturn(new AssinaturaTransferenciaService.AssinaturasTransferenciaArmazenadas("FISICA", "FISICA"));

        MovimentacaoBulkRequest request = new MovimentacaoBulkRequest(
                idOrigem,
                idDestino,
                List.of(new MovimentacaoBulkRequest.ItemMovimentacaoRequest(idMaterial, new BigDecimal("10.000"), null, CondicaoMaterial.NOVO)),
                "FISICA",
                "FISICA"
        );

        UUID idReferencia = useCases.transferenciaLote(request, idOperador);

        assertNotNull(idReferencia);
        verify(assinaturaTransferenciaService).armazenar(idReferencia, "FISICA", "FISICA");
        ArgumentCaptor<MovimentacaoEstoque> captor = ArgumentCaptor.forClass(MovimentacaoEstoque.class);
        verify(repository).save(captor.capture());

        MovimentacaoEstoque salva = captor.getValue();
        assertEquals("FISICA", salva.getAssinaturaEntregador());
        assertEquals("FISICA", salva.getAssinaturaRecebedor());
        assertEquals(MovimentacaoEstoque.TipoMovimentacao.TRANSFERENCIA, salva.getTipo());
        assertEquals(origem, salva.getEstoqueOrigem());
        assertEquals(destino, salva.getEstoqueDestino());
    }
}

