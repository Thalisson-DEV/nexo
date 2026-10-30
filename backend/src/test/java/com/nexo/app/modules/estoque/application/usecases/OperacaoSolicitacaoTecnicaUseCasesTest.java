package com.nexo.app.modules.estoque.application.usecases;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.obras.ObraApi.ObraPublica;
import com.nexo.app.modules.obras.ObraApi.TipoObraPublico;
import com.nexo.app.modules.estoque.application.dto.ConfirmarEntregaTecnicaRequest;
import com.nexo.app.modules.estoque.application.dto.ConfirmarDevolucaoTecnicaRequest;
import com.nexo.app.modules.estoque.application.dto.EstornarOperacaoTecnicaRequest;
import com.nexo.app.modules.estoque.application.services.GravadorEventoSolicitacaoService;
import com.nexo.app.modules.estoque.application.services.DebitoSaldoService;
import com.nexo.app.modules.estoque.application.services.ObservabilidadeSolicitacaoTecnica;
import com.nexo.app.modules.estoque.application.services.PublicadorEventoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.application.services.SerializadorPayloadEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.ComandoIdempotenteSolicitacao;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.ItemSolicitacao;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.MovimentacaoEstoque;
import com.nexo.app.modules.estoque.domain.OperacaoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.OrigemComandoTecnico;
import com.nexo.app.modules.estoque.domain.PayloadEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.ReservaSolicitacaoTecnica;
import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import com.nexo.app.modules.estoque.domain.SaldoSerie;
import com.nexo.app.modules.estoque.domain.Saldo;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.StatusSeparacao;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.modules.estoque.domain.TipoOperacaoSolicitacao;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.infrastructure.persistence.MovimentacaoEstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.ItemOperacaoSolicitacaoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.ComandoIdempotenteSolicitacaoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.OperacaoSolicitacaoMaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.ReservaSolicitacaoTecnicaRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoSerieRepository;
import com.nexo.app.modules.estoque.domain.MotivoPendencia;
import com.nexo.app.modules.estoque.infrastructure.persistence.MotivoPendenciaRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.ConflitoVersaoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OperacaoSolicitacaoTecnicaUseCasesTest {

    @Mock private SolicitacaoMaterialRepository solicitacaoRepository;
    @Mock private OperacaoSolicitacaoMaterialRepository operacaoRepository;
    @Mock private ItemOperacaoSolicitacaoRepository itemOperacaoRepository;
    @Mock private ComandoIdempotenteSolicitacaoRepository comandoRepository;
    @Mock private SaldoRepository saldoRepository;
    @Mock private SaldoSerieRepository saldoSerieRepository;
    @Mock private MovimentacaoEstoqueRepository movimentacaoRepository;
    @Mock private ReferenciaTecnicaValidator referenciaValidator;
    @Mock private ReservaSolicitacaoTecnicaUseCases reservaUseCases;
    @Mock private ReservaSolicitacaoTecnicaRepository reservaRepository;
    @Mock private GravadorEventoSolicitacaoService gravadorEvento;
    @Mock private PublicadorEventoSolicitacaoTecnica publicadorEventos;
    @Mock private ObservabilidadeSolicitacaoTecnica observabilidade;
    @Mock private DebitoSaldoService debitoSaldoService;
    @Mock private EstoqueCampoEquipeProvisioningService estoqueCampoEquipeService;
    @Mock private MotivoPendenciaRepository motivoPendenciaRepository;
    @InjectMocks private OperacaoSolicitacaoTecnicaUseCases useCases;

    private Estoque estoqueEquipe;

    @BeforeEach
    void configurarEstoqueEquipe() {
        estoqueEquipe = comId(new Estoque("EQUIPE-001", comId(new TipoEstoque("CAMPO"))));
        org.mockito.Mockito.lenient()
                .when(estoqueCampoEquipeService.resolver(any())).thenReturn(estoqueEquipe);
        MotivoPendencia motivoPendenteReserva = new MotivoPendencia("Pendente Reserva", "Pendente Reserva");
        ReflectionTestUtils.setField(motivoPendenteReserva, "id", UUID.randomUUID());
        org.mockito.Mockito.lenient()
                .when(motivoPendenciaRepository.findByNomeIgnoreCase("Pendente Reserva"))
                .thenReturn(Optional.of(motivoPendenteReserva));
        org.mockito.Mockito.lenient()
                .when(motivoPendenciaRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void rejeitaVersaoObsoletaDaOperacaoComVersaoAtualParaRecarga() {
        SolicitacaoMaterial solicitacao = solicitacaoPronta(true);
        ConfirmarEntregaTecnicaRequest base = request(solicitacao, UUID.randomUUID());
        ConfirmarEntregaTecnicaRequest obsoleto = new ConfirmarEntregaTecnicaRequest(
                solicitacao.getVersao() - 1, base.idEquipe(), base.assinaturaAlmox(),
                base.assinaturaEquipe(), base.itens());
        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));

        assertThatThrownBy(() -> useCases.entregar(
                solicitacao.getId(), obsoleto, UUID.randomUUID(), OrigemComandoTecnico.UI))
                .isInstanceOfSatisfying(ConflitoVersaoException.class, ex -> {
                    assertThat(ex.getVersaoEsperada()).isEqualTo(obsoleto.versaoEsperada());
                    assertThat(ex.getVersaoAtual()).isEqualTo(solicitacao.getVersao());
                });

        verify(operacaoRepository, never()).saveAndFlush(any());
    }

    @Test
    void entregaTecnicaAumentaSomenteCustodiaDaObra() {
        SolicitacaoMaterial solicitacao = solicitacaoPronta(true);
        UUID idUsuario = UUID.randomUUID();
        ConfirmarEntregaTecnicaRequest request = request(solicitacao, UUID.randomUUID());
        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(operacaoRepository.saveAndFlush(any())).thenAnswer(invocacao -> {
            OperacaoSolicitacaoMaterial operacao = invocacao.getArgument(0);
            ReflectionTestUtils.setField(operacao, "id", UUID.randomUUID());
            operacao.getItens().forEach(item -> ReflectionTestUtils.setField(item, "id", UUID.randomUUID()));
            return operacao;
        });
        when(saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(any(), any(), any()))
                .thenReturn(Optional.empty());

        UUID idOperacao = useCases.entregar(solicitacao.getId(), request, idUsuario, OrigemComandoTecnico.UI);

        assertThat(idOperacao).isNotNull();
        assertThat(solicitacao.getStatus()).isEqualTo(SolicitacaoMaterial.StatusSolicitacao.ENTREGUE);
        assertThat(solicitacao.getItens().getFirst().getQuantidadeEntregue()).isEqualByComparingTo("2.000");
        ArgumentCaptor<Saldo> saldo = ArgumentCaptor.forClass(Saldo.class);
        verify(saldoRepository).save(saldo.capture());
        assertThat(saldo.getValue().getEstoque()).isSameAs(estoqueEquipe);
        assertThat(saldo.getValue().getQuantidade()).isEqualByComparingTo("2.000");
        ArgumentCaptor<MovimentacaoEstoque> movimento = ArgumentCaptor.forClass(MovimentacaoEstoque.class);
        verify(movimentacaoRepository).save(movimento.capture());
        assertThat(movimento.getValue().getEstoqueOrigem()).isSameAs(solicitacao.getAlmoxarifadoAtendimento());
        assertThat(movimento.getValue().getEstoqueDestino()).isSameAs(estoqueEquipe);
        assertThat(movimento.getValue().getIdObra()).isEqualTo(solicitacao.getIdObra());
        assertThat(movimento.getValue().getSolicitacao()).isSameAs(solicitacao);
        assertThat(movimento.getValue().getTipoTecnico()).isEqualTo(solicitacao.getTipoTecnico());
        assertThat(movimento.getValue().getTipoObraSnapshot()).isEqualTo(solicitacao.getTipoObraSnapshot());
        assertThat(movimento.getValue().getOcorrencia()).isEqualTo(solicitacao.getOcorrencia());
        assertThat(movimento.getValue().getReserva()).isSameAs(solicitacao.getReserva());
        assertThat(movimento.getValue().getOperacaoSolicitacao()).isNotNull();
        assertThat(movimento.getValue().getItemOperacao()).isNotNull();
        assertThat(movimento.getValue().getIdEquipe()).isEqualTo(request.idEquipe());
        ArgumentCaptor<PayloadEventoSolicitacao> payload =
                ArgumentCaptor.forClass(PayloadEventoSolicitacao.class);
        verify(gravadorEvento).registrarFilho(
                org.mockito.ArgumentMatchers.same(solicitacao),
                org.mockito.ArgumentMatchers.eq(
                        com.nexo.app.modules.estoque.domain.TipoEventoSolicitacao.ENTREGA_CONFIRMADA),
                payload.capture(), any());
        assertThat(payload.getValue().snapshots())
                .containsEntry("comprovantesRegistrados", true)
                .doesNotContainKeys("assinaturaAlmox", "assinaturaEquipe");
        assertThat(payload.getValue().alteracoes())
                .extracting(com.nexo.app.modules.estoque.domain.AlteracaoEventoSolicitacao::caminho)
                .containsExactlyInAnyOrder(
                        "itens[" + solicitacao.getItens().getFirst().getId() + "].quantidadeEntregaLiquida",
                        "itens[" + solicitacao.getItens().getFirst().getId() + "].quantidadePendente",
                        "status");
        var alteracaoEntrega = payload.getValue().alteracoes().stream()
                .filter(a -> a.caminho().endsWith(".quantidadeEntregaLiquida"))
                .findFirst().orElseThrow();
        assertThat(alteracaoEntrega.valorAnterior()).isEqualTo(new BigDecimal("0.000"));
        assertThat(alteracaoEntrega.valorNovo()).isEqualTo(new BigDecimal("2.000"));
        var alteracaoPendente = payload.getValue().alteracoes().stream()
                .filter(a -> a.caminho().endsWith(".quantidadePendente"))
                .findFirst().orElseThrow();
        assertThat(alteracaoPendente.valorAnterior()).isEqualTo(new BigDecimal("2.000"));
        assertThat(alteracaoPendente.valorNovo()).isEqualTo(new BigDecimal("0.000"));
        String payloadCanonico = new SerializadorPayloadEventoSolicitacao(new ObjectMapper())
                .serializar(payload.getValue());
        assertThat(payloadCanonico)
                .contains("\"comprovantesRegistrados\":true")
                .doesNotContain("assinatura", "assinatura almox", "assinatura equipe");
    }

    @Test
    void rejeitaEntregaTecnicaSemReservaAntesDePersistir() {
        SolicitacaoMaterial solicitacao = solicitacaoPronta(false);
        ConfirmarEntregaTecnicaRequest request = request(solicitacao, UUID.randomUUID());
        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));

        assertThatThrownBy(() -> useCases.entregar(
                solicitacao.getId(), request, UUID.randomUUID(), OrigemComandoTecnico.UI))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("reserva valida");
        verify(operacaoRepository, never()).saveAndFlush(any());
        verify(saldoRepository, never()).save(any());
    }

    @Test
    void devolucaoTecnicaBaixaSomenteCustodiaDaObraSemSeparacao() {
        SolicitacaoMaterial solicitacao = solicitacaoPronta(true, TipoSolicitacaoTecnica.DEVOLUCAO_COMUM);
        Saldo saldoObra = new Saldo(
                solicitacao.getEstoqueOrigem(), solicitacao.getItens().getFirst().getMaterial(),
                com.nexo.app.modules.estoque.domain.CondicaoMaterial.NOVO);
        saldoObra.adicionarQuantidade(new BigDecimal("2.000"));
        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(saldoRepository.findWithLockByEstoqueIdAndMaterialIdAndCondicao(any(), any(), any()))
                .thenReturn(Optional.of(saldoObra));
        when(operacaoRepository.saveAndFlush(any())).thenAnswer(invocacao -> {
            OperacaoSolicitacaoMaterial operacao = invocacao.getArgument(0);
            ReflectionTestUtils.setField(operacao, "id", UUID.randomUUID());
            operacao.getItens().forEach(item -> ReflectionTestUtils.setField(item, "id", UUID.randomUUID()));
            return operacao;
        });
        ConfirmarDevolucaoTecnicaRequest request = new ConfirmarDevolucaoTecnicaRequest(
                0L, UUID.randomUUID(), List.of(new ConfirmarDevolucaoTecnicaRequest.ItemDevolucaoTecnicaRequest(
                solicitacao.getItens().getFirst().getId(), new BigDecimal("2.000"), null)));

        useCases.devolver(solicitacao.getId(), request, UUID.randomUUID(), OrigemComandoTecnico.UI);

        assertThat(solicitacao.getStatus()).isEqualTo(SolicitacaoMaterial.StatusSolicitacao.DEVOLVIDO);
        assertThat(solicitacao.possuiPendencia()).isFalse();
        assertThat(solicitacao.getItens().getFirst().possuiPendencia()).isFalse();
        verify(debitoSaldoService).debitar(
                estoqueEquipe, solicitacao.getItens().getFirst().getMaterial(),
                CondicaoMaterial.NOVO, new BigDecimal("2.000"));
        ArgumentCaptor<MovimentacaoEstoque> movimento = ArgumentCaptor.forClass(MovimentacaoEstoque.class);
        verify(movimentacaoRepository).save(movimento.capture());
        assertThat(movimento.getValue().getEstoqueOrigem()).isSameAs(estoqueEquipe);
        assertThat(movimento.getValue().getEstoqueDestino()).isSameAs(solicitacao.getAlmoxarifadoAtendimento());
        assertThat(movimento.getValue().getTipo()).isEqualTo(MovimentacaoEstoque.TipoMovimentacao.DEVOLUCAO);
        ArgumentCaptor<PayloadEventoSolicitacao> payload =
                ArgumentCaptor.forClass(PayloadEventoSolicitacao.class);
        verify(gravadorEvento).registrarFilho(any(), any(), payload.capture(), any());
        assertThat(payload.getValue().alteracoes())
                .extracting(com.nexo.app.modules.estoque.domain.AlteracaoEventoSolicitacao::caminho)
                .contains("itens[" + solicitacao.getItens().getFirst().getId()
                        + "].quantidadeDevolucaoLiquida", "status");
    }

    @Test
    void devolucaoTecnicaSemPlanejamentoNemReservaExecutaComSucesso() {
        SolicitacaoMaterial solicitacao = solicitacaoPronta(false, TipoSolicitacaoTecnica.DEVOLUCAO_COMUM);
        ReflectionTestUtils.setField(solicitacao, "dataPrevistaRetirada", null);
        Saldo saldoObra = new Saldo(
                solicitacao.getEstoqueOrigem(), solicitacao.getItens().getFirst().getMaterial(),
                com.nexo.app.modules.estoque.domain.CondicaoMaterial.NOVO);
        saldoObra.adicionarQuantidade(new BigDecimal("2.000"));
        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(saldoRepository.findWithLockByEstoqueIdAndMaterialIdAndCondicao(any(), any(), any()))
                .thenReturn(Optional.of(saldoObra));
        when(operacaoRepository.saveAndFlush(any())).thenAnswer(invocacao -> {
            OperacaoSolicitacaoMaterial operacao = invocacao.getArgument(0);
            ReflectionTestUtils.setField(operacao, "id", UUID.randomUUID());
            operacao.getItens().forEach(item -> ReflectionTestUtils.setField(item, "id", UUID.randomUUID()));
            return operacao;
        });
        ConfirmarDevolucaoTecnicaRequest request = new ConfirmarDevolucaoTecnicaRequest(
                0L, UUID.randomUUID(), List.of(new ConfirmarDevolucaoTecnicaRequest.ItemDevolucaoTecnicaRequest(
                solicitacao.getItens().getFirst().getId(), new BigDecimal("2.000"), null)));

        UUID idOperacao = useCases.devolver(solicitacao.getId(), request, UUID.randomUUID(), OrigemComandoTecnico.UI);

        assertThat(idOperacao).isNotNull();
        assertThat(solicitacao.getStatus()).isEqualTo(SolicitacaoMaterial.StatusSolicitacao.DEVOLVIDO);
        assertThat(solicitacao.possuiPendencia()).isTrue();
        assertThat(solicitacao.getMotivoPendencia().getNome()).isEqualTo("Pendente Reserva");
        assertThat(solicitacao.getJustificativaPendencia()).isNull();
        ItemSolicitacao itemDevolvido = solicitacao.getItens().getFirst();
        assertThat(itemDevolvido.possuiPendencia()).isTrue();
        assertThat(itemDevolvido.getMotivoPendencia().getNome()).isEqualTo("Pendente Reserva");
        assertThat(itemDevolvido.getJustificativaPendencia()).isNull();
    }

    @Test
    void devolucaoTecnicaTipo922SemReservaMarcaPendenciaPendenteReserva() {
        SolicitacaoMaterial solicitacao = solicitacaoPronta(false, TipoSolicitacaoTecnica.DEVOLUCAO_CONCRETO);
        Saldo saldoObra = new Saldo(
                solicitacao.getEstoqueOrigem(), solicitacao.getItens().getFirst().getMaterial(),
                com.nexo.app.modules.estoque.domain.CondicaoMaterial.NOVO);
        saldoObra.adicionarQuantidade(new BigDecimal("2.000"));
        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(saldoRepository.findWithLockByEstoqueIdAndMaterialIdAndCondicao(any(), any(), any()))
                .thenReturn(Optional.of(saldoObra));
        when(operacaoRepository.saveAndFlush(any())).thenAnswer(invocacao -> {
            OperacaoSolicitacaoMaterial operacao = invocacao.getArgument(0);
            ReflectionTestUtils.setField(operacao, "id", UUID.randomUUID());
            operacao.getItens().forEach(item -> ReflectionTestUtils.setField(item, "id", UUID.randomUUID()));
            return operacao;
        });
        ConfirmarDevolucaoTecnicaRequest request = new ConfirmarDevolucaoTecnicaRequest(
                0L, UUID.randomUUID(), List.of(new ConfirmarDevolucaoTecnicaRequest.ItemDevolucaoTecnicaRequest(
                solicitacao.getItens().getFirst().getId(), new BigDecimal("2.000"), null)));

        UUID idOperacao = useCases.devolver(solicitacao.getId(), request, UUID.randomUUID(), OrigemComandoTecnico.UI);

        assertThat(idOperacao).isNotNull();
        assertThat(solicitacao.getStatus()).isEqualTo(SolicitacaoMaterial.StatusSolicitacao.DEVOLVIDO);
        assertThat(solicitacao.possuiPendencia()).isTrue();
        assertThat(solicitacao.getMotivoPendencia().getNome()).isEqualTo("Pendente Reserva");
        assertThat(solicitacao.getJustificativaPendencia()).isNull();
        ItemSolicitacao item = solicitacao.getItens().getFirst();
        assertThat(item.possuiPendencia()).isTrue();
        assertThat(item.getMotivoPendencia().getNome()).isEqualTo("Pendente Reserva");
        assertThat(item.getJustificativaPendencia()).isNull();
    }

    @Test
    void devolucaoTecnicaComReservaInformadaNoRequestNaoGeraPendencia() {
        SolicitacaoMaterial solicitacao = solicitacaoPronta(false, TipoSolicitacaoTecnica.DEVOLUCAO_COMUM);
        Saldo saldoObra = new Saldo(
                solicitacao.getEstoqueOrigem(), solicitacao.getItens().getFirst().getMaterial(),
                com.nexo.app.modules.estoque.domain.CondicaoMaterial.NOVO);
        saldoObra.adicionarQuantidade(new BigDecimal("2.000"));
        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(saldoRepository.findWithLockByEstoqueIdAndMaterialIdAndCondicao(any(), any(), any()))
                .thenReturn(Optional.of(saldoObra));
        when(operacaoRepository.saveAndFlush(any())).thenAnswer(invocacao -> {
            OperacaoSolicitacaoMaterial operacao = invocacao.getArgument(0);
            ReflectionTestUtils.setField(operacao, "id", UUID.randomUUID());
            operacao.getItens().forEach(item -> ReflectionTestUtils.setField(item, "id", UUID.randomUUID()));
            return operacao;
        });
        ReservaSolicitacaoTecnica reservaCriada = new ReservaSolicitacaoTecnica(
                solicitacao, new BigInteger("998877"), LocalDate.now(), null,
                null, null, UUID.randomUUID());
        ReflectionTestUtils.setField(solicitacao, "reserva", null);
        when(reservaRepository.findBySolicitacaoId(solicitacao.getId()))
                .thenReturn(Optional.of(reservaCriada));
        ConfirmarDevolucaoTecnicaRequest request = new ConfirmarDevolucaoTecnicaRequest(
                0L, UUID.randomUUID(), "998877", LocalDate.now(), null,
                List.of(new ConfirmarDevolucaoTecnicaRequest.ItemDevolucaoTecnicaRequest(
                        solicitacao.getItens().getFirst().getId(), new BigDecimal("2.000"), null)));

        UUID idOperacao = useCases.devolver(solicitacao.getId(), request, UUID.randomUUID(), OrigemComandoTecnico.UI);

        assertThat(idOperacao).isNotNull();
        assertThat(solicitacao.getStatus()).isEqualTo(SolicitacaoMaterial.StatusSolicitacao.DEVOLVIDO);
        assertThat(solicitacao.possuiPendencia()).isFalse();
        assertThat(solicitacao.getItens().getFirst().possuiPendencia()).isFalse();
        verify(reservaUseCases).registrarOuAlterar(any(), any(), any(), any());
    }

    @Test
    void entregaSeriadaCriaCustodiaDiretamenteNaObra() {
        SolicitacaoMaterial solicitacao = solicitacaoPronta(true, TipoSolicitacaoTecnica.ADICAO_COMUM, true);
        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(operacaoRepository.saveAndFlush(any())).thenAnswer(invocacao -> {
            OperacaoSolicitacaoMaterial operacao = invocacao.getArgument(0);
            ReflectionTestUtils.setField(operacao, "id", UUID.randomUUID());
            operacao.getItens().forEach(item -> ReflectionTestUtils.setField(item, "id", UUID.randomUUID()));
            return operacao;
        });
        when(saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(any(), any(), any()))
                .thenReturn(Optional.empty());
        SaldoSerie serieOrigem = new SaldoSerie(
                solicitacao.getAlmoxarifadoAtendimento(), solicitacao.getItens().getFirst().getMaterial(),
                "SERIE-001", CondicaoMaterial.NOVO);
        when(saldoSerieRepository.findByEstoqueIdAndMaterialIdAndNumSerieAndCondicaoAndAtivoTrue(
                any(), any(), org.mockito.ArgumentMatchers.eq("SERIE-001"), any()))
                .thenReturn(Optional.of(serieOrigem));
        ConfirmarEntregaTecnicaRequest request = new ConfirmarEntregaTecnicaRequest(
                0L, UUID.randomUUID(), "assinatura-almox", "assinatura-equipe",
                List.of(new ConfirmarEntregaTecnicaRequest.ItemEntregaTecnicaRequest(
                        solicitacao.getItens().getFirst().getId(), BigDecimal.ONE, "SERIE-001")));

        useCases.entregar(solicitacao.getId(), request, UUID.randomUUID(), OrigemComandoTecnico.UI);

        ArgumentCaptor<com.nexo.app.modules.estoque.domain.SaldoSerie> serie = ArgumentCaptor.forClass(
                com.nexo.app.modules.estoque.domain.SaldoSerie.class);
        verify(saldoSerieRepository).save(serie.capture());
        assertThat(serie.getValue().getEstoque()).isSameAs(estoqueEquipe);
        assertThat(serie.getValue().getNumSerie()).isEqualTo("SERIE-001");
    }

    @Test
    void entregaMultiplosItensSeriadosMesmoItemSolicitacaoComSucesso() {
        SolicitacaoMaterial solicitacao = solicitacaoPronta(true, TipoSolicitacaoTecnica.ADICAO_COMUM, true);
        solicitacao.getItens().getFirst().replanejar(solicitacao.getItens().getFirst().getMaterial(), new BigDecimal("3.000"));
        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(operacaoRepository.saveAndFlush(any())).thenAnswer(invocacao -> {
            OperacaoSolicitacaoMaterial operacao = invocacao.getArgument(0);
            ReflectionTestUtils.setField(operacao, "id", UUID.randomUUID());
            operacao.getItens().forEach(item -> ReflectionTestUtils.setField(item, "id", UUID.randomUUID()));
            return operacao;
        });
        when(saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(saldoSerieRepository.findByEstoqueIdAndMaterialIdAndNumSerieAndCondicaoAndAtivoTrue(
                any(), any(), any(), any())).thenAnswer(invocation -> Optional.of(new SaldoSerie(
                solicitacao.getAlmoxarifadoAtendimento(), solicitacao.getItens().getFirst().getMaterial(),
                invocation.getArgument(2), CondicaoMaterial.NOVO)));
        UUID idItem = solicitacao.getItens().getFirst().getId();
        ConfirmarEntregaTecnicaRequest request = new ConfirmarEntregaTecnicaRequest(
                0L, UUID.randomUUID(), "assinatura-almox", "assinatura-equipe",
                List.of(
                        new ConfirmarEntregaTecnicaRequest.ItemEntregaTecnicaRequest(idItem, BigDecimal.ONE, "12354215"),
                        new ConfirmarEntregaTecnicaRequest.ItemEntregaTecnicaRequest(idItem, BigDecimal.ONE, "32634523"),
                        new ConfirmarEntregaTecnicaRequest.ItemEntregaTecnicaRequest(idItem, BigDecimal.ONE, "4523744525")
                ));

        UUID idOperacao = useCases.entregar(solicitacao.getId(), request, UUID.randomUUID(), OrigemComandoTecnico.UI);

        assertThat(idOperacao).isNotNull();
        assertThat(solicitacao.getStatus()).isEqualTo(SolicitacaoMaterial.StatusSolicitacao.ENTREGUE);
        assertThat(solicitacao.getItens().getFirst().getQuantidadePendente()).isZero();
        verify(saldoSerieRepository, times(3)).save(any(com.nexo.app.modules.estoque.domain.SaldoSerie.class));
    }

    @Test
    void entregaMultiplosItensSeriadosRejeitaSeExcederSaldoPendente() {
        SolicitacaoMaterial solicitacao = solicitacaoPronta(true, TipoSolicitacaoTecnica.ADICAO_COMUM, true);
        solicitacao.getItens().getFirst().replanejar(solicitacao.getItens().getFirst().getMaterial(), new BigDecimal("2.000"));
        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(saldoSerieRepository.findByNumSerieAndAtivoTrue(any())).thenReturn(Optional.empty());
        UUID idItem = solicitacao.getItens().getFirst().getId();
        ConfirmarEntregaTecnicaRequest request = new ConfirmarEntregaTecnicaRequest(
                0L, UUID.randomUUID(), "assinatura-almox", "assinatura-equipe",
                List.of(
                        new ConfirmarEntregaTecnicaRequest.ItemEntregaTecnicaRequest(idItem, BigDecimal.ONE, "12354215"),
                        new ConfirmarEntregaTecnicaRequest.ItemEntregaTecnicaRequest(idItem, BigDecimal.ONE, "32634523"),
                        new ConfirmarEntregaTecnicaRequest.ItemEntregaTecnicaRequest(idItem, BigDecimal.ONE, "4523744525")
                ));

        assertThatThrownBy(() -> useCases.entregar(solicitacao.getId(), request, UUID.randomUUID(), OrigemComandoTecnico.UI))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("excede o saldo pendente");
    }

    @Test
    void entregaMaterialNaoSeriadoRejeitaItemDuplicado() {
        SolicitacaoMaterial solicitacao = solicitacaoPronta(true, TipoSolicitacaoTecnica.ADICAO_COMUM, false);
        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        UUID idItem = solicitacao.getItens().getFirst().getId();
        ConfirmarEntregaTecnicaRequest request = new ConfirmarEntregaTecnicaRequest(
                0L, UUID.randomUUID(), "assinatura-almox", "assinatura-equipe",
                List.of(
                        new ConfirmarEntregaTecnicaRequest.ItemEntregaTecnicaRequest(idItem, BigDecimal.ONE, null),
                        new ConfirmarEntregaTecnicaRequest.ItemEntregaTecnicaRequest(idItem, BigDecimal.ONE, null)
                ));

        assertThatThrownBy(() -> useCases.entregar(solicitacao.getId(), request, UUID.randomUUID(), OrigemComandoTecnico.UI))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("mais de uma vez na entrega");
    }

    @Test
    void devolucaoMultiplosItensSeriadosMesmoItemSolicitacaoComSucesso() {
        SolicitacaoMaterial solicitacao = solicitacaoPronta(true, TipoSolicitacaoTecnica.DEVOLUCAO_COMUM, true);
        solicitacao.getItens().getFirst().replanejar(solicitacao.getItens().getFirst().getMaterial(), new BigDecimal("2.000"));
        ItemSolicitacao item = solicitacao.getItens().getFirst();
        Saldo saldoObra = new Saldo(estoqueEquipe, item.getMaterial(), CondicaoMaterial.NOVO);
        saldoObra.adicionarQuantidade(new BigDecimal("2.000"));
        SaldoSerie serie1 = new SaldoSerie(estoqueEquipe, item.getMaterial(), "SERIE-1", CondicaoMaterial.NOVO);
        SaldoSerie serie2 = new SaldoSerie(estoqueEquipe, item.getMaterial(), "SERIE-2", CondicaoMaterial.NOVO);

        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(saldoRepository.findWithLockByEstoqueIdAndMaterialIdAndCondicao(any(), any(), any()))
                .thenReturn(Optional.of(saldoObra));
        when(saldoSerieRepository.findByEstoqueIdAndMaterialIdAndNumSerieAndCondicaoAndAtivoTrue(any(), any(), org.mockito.ArgumentMatchers.eq("SERIE-1"), any()))
                .thenReturn(Optional.of(serie1));
        when(saldoSerieRepository.findByEstoqueIdAndMaterialIdAndNumSerieAndCondicaoAndAtivoTrue(any(), any(), org.mockito.ArgumentMatchers.eq("SERIE-2"), any()))
                .thenReturn(Optional.of(serie2));
        prepararPersistenciaOperacao();

        UUID idItem = item.getId();
        ConfirmarDevolucaoTecnicaRequest request = new ConfirmarDevolucaoTecnicaRequest(
                0L, UUID.randomUUID(),
                List.of(
                        new ConfirmarDevolucaoTecnicaRequest.ItemDevolucaoTecnicaRequest(idItem, BigDecimal.ONE, "SERIE-1"),
                        new ConfirmarDevolucaoTecnicaRequest.ItemDevolucaoTecnicaRequest(idItem, BigDecimal.ONE, "SERIE-2")
                ));

        UUID idOperacao = useCases.devolver(solicitacao.getId(), request, UUID.randomUUID(), OrigemComandoTecnico.UI);

        assertThat(idOperacao).isNotNull();
        assertThat(solicitacao.getStatus()).isEqualTo(SolicitacaoMaterial.StatusSolicitacao.DEVOLVIDO);
        verify(debitoSaldoService, times(2)).debitar(
                org.mockito.ArgumentMatchers.same(estoqueEquipe),
                org.mockito.ArgumentMatchers.same(item.getMaterial()),
                org.mockito.ArgumentMatchers.eq(CondicaoMaterial.NOVO),
                org.mockito.ArgumentMatchers.eq(BigDecimal.ONE));
        assertThat(serie1.isAtivo()).isTrue();
        assertThat(serie2.isAtivo()).isTrue();
        assertThat(serie1.getEstoque()).isSameAs(solicitacao.getAlmoxarifadoAtendimento());
        assertThat(serie2.getEstoque()).isSameAs(solicitacao.getAlmoxarifadoAtendimento());
    }

    @Test
    void estornoDeEntregaReduzCustodiaDaObraERegistraEventoCorrelacionado() {
        SolicitacaoMaterial solicitacao = solicitacaoPronta(true);
        ItemSolicitacao item = solicitacao.getItens().getFirst();
        item.registrarEntregaTecnica(new BigDecimal("2.000"));
        solicitacao.confirmarEntregaTecnica();
        OperacaoSolicitacaoMaterial original = operacaoOriginal(
                solicitacao, TipoOperacaoSolicitacao.ENTREGA, new BigDecimal("2.000"));
        Saldo saldoObra = new Saldo(
                solicitacao.getEstoqueDestino(), item.getMaterial(),
                com.nexo.app.modules.estoque.domain.CondicaoMaterial.NOVO);
        saldoObra.adicionarQuantidade(new BigDecimal("2.000"));
        EstornarOperacaoTecnicaRequest request = estornoRequest(solicitacao, original, new BigDecimal("2.000"));

        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(operacaoRepository.findById(original.getId())).thenReturn(Optional.of(original));
        when(itemOperacaoRepository.somarQuantidadeEstornadaPorItemOriginal(any())).thenReturn(BigDecimal.ZERO);
        when(saldoRepository.findWithLockByEstoqueIdAndMaterialIdAndCondicao(any(), any(), any()))
                .thenReturn(Optional.of(saldoObra));
        prepararPersistenciaOperacao();

        UUID idEstorno = useCases.estornar(solicitacao.getId(), request, UUID.randomUUID(), OrigemComandoTecnico.UI);

        assertThat(idEstorno).isNotNull();
        verify(debitoSaldoService).debitar(
                estoqueEquipe, item.getMaterial(), CondicaoMaterial.NOVO, new BigDecimal("2.000"));
        assertThat(solicitacao.getStatus()).isEqualTo(SolicitacaoMaterial.StatusSolicitacao.ESTORNADO);
        ArgumentCaptor<MovimentacaoEstoque> movimento = ArgumentCaptor.forClass(MovimentacaoEstoque.class);
        verify(movimentacaoRepository).save(movimento.capture());
        assertThat(movimento.getValue().getTipo()).isEqualTo(MovimentacaoEstoque.TipoMovimentacao.ESTORNO);
        assertThat(movimento.getValue().getEstoqueOrigem()).isSameAs(estoqueEquipe);
        assertThat(movimento.getValue().getEstoqueDestino()).isSameAs(solicitacao.getAlmoxarifadoAtendimento());
        ArgumentCaptor<PayloadEventoSolicitacao> payload =
                ArgumentCaptor.forClass(PayloadEventoSolicitacao.class);
        verify(gravadorEvento).registrarFilho(
                org.mockito.ArgumentMatchers.same(solicitacao),
                org.mockito.ArgumentMatchers.eq(com.nexo.app.modules.estoque.domain.TipoEventoSolicitacao.ESTORNO_CONFIRMADO),
                payload.capture(), any());
        assertThat(payload.getValue().alteracoes())
                .extracting(com.nexo.app.modules.estoque.domain.AlteracaoEventoSolicitacao::caminho)
                .contains("itens[" + item.getId() + "].quantidadeEntregaLiquida", "status");
        var quantidade = payload.getValue().alteracoes().stream()
                .filter(alteracao -> alteracao.caminho().endsWith("quantidadeEntregaLiquida"))
                .findFirst().orElseThrow();
        assertThat(quantidade.valorAnterior()).isEqualTo(new BigDecimal("2.000"));
        assertThat(quantidade.valorNovo()).isEqualTo(new BigDecimal("0.000"));
        verify(publicadorEventos).publicarFilho(
                org.mockito.ArgumentMatchers.eq(
                        com.nexo.app.shared.domain.events.SolicitacaoMaterialTecnicaEvent.Tipo.ESTORNO_TECNICO),
                org.mockito.ArgumentMatchers.same(solicitacao),
                org.mockito.ArgumentMatchers.eq(idEstorno),
                org.mockito.ArgumentMatchers.eq(original.getId()));
    }

    @Test
    void estornoDeDevolucaoRecompoeCustodiaDaObra() {
        SolicitacaoMaterial solicitacao = solicitacaoPronta(true, TipoSolicitacaoTecnica.DEVOLUCAO_COMUM);
        ItemSolicitacao item = solicitacao.getItens().getFirst();
        item.registrarDevolucaoTecnica(new BigDecimal("2.000"));
        solicitacao.confirmarDevolucaoTecnica();
        OperacaoSolicitacaoMaterial original = operacaoOriginal(
                solicitacao, TipoOperacaoSolicitacao.DEVOLUCAO, new BigDecimal("2.000"));
        EstornarOperacaoTecnicaRequest request = estornoRequest(solicitacao, original, new BigDecimal("2.000"));

        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(operacaoRepository.findById(original.getId())).thenReturn(Optional.of(original));
        when(itemOperacaoRepository.somarQuantidadeEstornadaPorItemOriginal(any())).thenReturn(BigDecimal.ZERO);
        when(saldoRepository.findWithLockByEstoqueIdAndMaterialIdAndCondicao(any(), any(), any()))
                .thenReturn(Optional.empty());
        prepararPersistenciaOperacao();

        useCases.estornar(solicitacao.getId(), request, UUID.randomUUID(), OrigemComandoTecnico.UI);

        ArgumentCaptor<Saldo> saldo = ArgumentCaptor.forClass(Saldo.class);
        verify(saldoRepository).save(saldo.capture());
        assertThat(saldo.getValue().getEstoque()).isSameAs(estoqueEquipe);
        assertThat(saldo.getValue().getQuantidade()).isEqualByComparingTo("2.000");
        ArgumentCaptor<MovimentacaoEstoque> movimento = ArgumentCaptor.forClass(MovimentacaoEstoque.class);
        verify(movimentacaoRepository).save(movimento.capture());
        assertThat(movimento.getValue().getEstoqueOrigem()).isSameAs(solicitacao.getAlmoxarifadoAtendimento());
        assertThat(movimento.getValue().getEstoqueDestino()).isSameAs(estoqueEquipe);
    }

    @Test
    void rejeitaEstornoAcimaDoDisponivelNaOperacaoOriginal() {
        SolicitacaoMaterial solicitacao = solicitacaoPronta(true);
        ItemSolicitacao item = solicitacao.getItens().getFirst();
        item.registrarEntregaTecnica(new BigDecimal("2.000"));
        solicitacao.confirmarEntregaTecnica();
        OperacaoSolicitacaoMaterial original = operacaoOriginal(
                solicitacao, TipoOperacaoSolicitacao.ENTREGA, new BigDecimal("2.000"));
        EstornarOperacaoTecnicaRequest request = estornoRequest(solicitacao, original, new BigDecimal("2.000"));

        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(operacaoRepository.findById(original.getId())).thenReturn(Optional.of(original));
        when(itemOperacaoRepository.somarQuantidadeEstornadaPorItemOriginal(any())).thenReturn(BigDecimal.ONE);

        assertThatThrownBy(() -> useCases.estornar(
                solicitacao.getId(), request, UUID.randomUUID(), OrigemComandoTecnico.UI))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("ainda reversivel");
        verify(operacaoRepository, never()).saveAndFlush(any());
    }

    @Test
    void reenvioDaMesmaEntregaComChaveIdempotenteRetornaAOperacaoOriginal() {
        SolicitacaoMaterial solicitacao = solicitacaoPronta(true);
        UUID idUsuario = UUID.randomUUID();
        ConfirmarEntregaTecnicaRequest request = request(solicitacao, UUID.randomUUID());
        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(comandoRepository.findByEscopoAndIdUsuarioAndChave("ENTREGA_TECNICA", idUsuario, "entrega-1"))
                .thenReturn(Optional.empty());
        when(operacaoRepository.saveAndFlush(any())).thenAnswer(invocacao -> {
            OperacaoSolicitacaoMaterial operacao = invocacao.getArgument(0);
            ReflectionTestUtils.setField(operacao, "id", UUID.randomUUID());
            operacao.getItens().forEach(item -> ReflectionTestUtils.setField(item, "id", UUID.randomUUID()));
            return operacao;
        });
        when(saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(any(), any(), any()))
                .thenReturn(Optional.empty());

        UUID primeira = useCases.entregar(solicitacao.getId(), request, idUsuario, OrigemComandoTecnico.UI, "entrega-1");

        ArgumentCaptor<ComandoIdempotenteSolicitacao> comando =
                ArgumentCaptor.forClass(ComandoIdempotenteSolicitacao.class);
        verify(comandoRepository).save(comando.capture());
        when(comandoRepository.findByEscopoAndIdUsuarioAndChave("ENTREGA_TECNICA", idUsuario, "entrega-1"))
                .thenReturn(Optional.of(comando.getValue()));

        UUID repetida = useCases.entregar(solicitacao.getId(), request, idUsuario, OrigemComandoTecnico.UI, "entrega-1");

        assertThat(repetida).isEqualTo(primeira);
        verify(operacaoRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void reutilizacaoDaChaveComPayloadDiferenteERejeitada() {
        SolicitacaoMaterial solicitacao = solicitacaoPronta(true);
        UUID idUsuario = UUID.randomUUID();
        ConfirmarEntregaTecnicaRequest request = request(solicitacao, UUID.randomUUID());
        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(comandoRepository.findByEscopoAndIdUsuarioAndChave("ENTREGA_TECNICA", idUsuario, "entrega-2"))
                .thenReturn(Optional.empty());
        when(operacaoRepository.saveAndFlush(any())).thenAnswer(invocacao -> {
            OperacaoSolicitacaoMaterial operacao = invocacao.getArgument(0);
            ReflectionTestUtils.setField(operacao, "id", UUID.randomUUID());
            operacao.getItens().forEach(item -> ReflectionTestUtils.setField(item, "id", UUID.randomUUID()));
            return operacao;
        });
        when(saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(any(), any(), any()))
                .thenReturn(Optional.empty());
        useCases.entregar(solicitacao.getId(), request, idUsuario, OrigemComandoTecnico.UI, "entrega-2");
        ArgumentCaptor<ComandoIdempotenteSolicitacao> comando =
                ArgumentCaptor.forClass(ComandoIdempotenteSolicitacao.class);
        verify(comandoRepository).save(comando.capture());
        when(comandoRepository.findByEscopoAndIdUsuarioAndChave("ENTREGA_TECNICA", idUsuario, "entrega-2"))
                .thenReturn(Optional.of(comando.getValue()));
        ConfirmarEntregaTecnicaRequest payloadDiferente = new ConfirmarEntregaTecnicaRequest(
                request.versaoEsperada(), request.idEquipe(), request.assinaturaAlmox(), request.assinaturaEquipe(), List.of(
                new ConfirmarEntregaTecnicaRequest.ItemEntregaTecnicaRequest(
                        request.itens().getFirst().idItemSolicitacao(), BigDecimal.ONE, null)));

        assertThatThrownBy(() -> useCases.entregar(
                solicitacao.getId(), payloadDiferente, idUsuario, OrigemComandoTecnico.UI, "entrega-2"))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("outro payload");
        verify(operacaoRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void movimentosDeMultiplosItensPreservamOcorrenciaDoFatoAposCorrecaoDoPlanejamento() {
        SolicitacaoMaterial solicitacao = solicitacaoPronta(true);
        Material segundoMaterial = comId(new com.nexo.app.modules.estoque.domain.Material(
                "MAT-ENTREGA-2", "Material adicional", "UN", "MATR", null, null,
                false, UUID.randomUUID()));
        solicitacao.adicionarItemTecnico(segundoMaterial, BigDecimal.ONE);
        ReflectionTestUtils.setField(solicitacao.getItens().get(1), "id", UUID.randomUUID());
        OperacaoSolicitacaoMaterial operacao = new OperacaoSolicitacaoMaterial(
                solicitacao, TipoOperacaoSolicitacao.ENTREGA, solicitacao.getIdEquipeSaque(), UUID.randomUUID(),
                null, "assinatura-almox", "assinatura-equipe", OrigemComandoTecnico.UI, null);
        solicitacao.getItens().forEach(item -> operacao.adicionarItem(item, BigDecimal.ONE, null, null));
        ReflectionTestUtils.setField(operacao, "id", UUID.randomUUID());
        operacao.getItens().forEach(item -> ReflectionTestUtils.setField(item, "id", UUID.randomUUID()));
        List<MovimentacaoEstoque> movimentos = operacao.getItens().stream()
                .map(item -> MovimentacaoEstoque.criarTecnica(
                        null, solicitacao.getEstoqueDestino(), item,
                        MovimentacaoEstoque.TipoMovimentacao.ENTREGA, operacao.getIdUsuarioOperador()))
                .toList();

        ObraPublica obraCorretiva = new ObraPublica(
                solicitacao.getIdObra(), "001", "Obra corrigida", TipoObraPublico.CORRETIVA, true,
                UUID.randomUUID(), "OBRAS", true, UUID.randomUUID(), Set.of(), null, List.of());
        solicitacao.substituirPlanejamentoTecnico(
                obraCorretiva,
                new com.nexo.app.modules.obras.ObraApi.OcorrenciaPublica(
                        UUID.randomUUID(), obraCorretiva.id(), "OC-1042"),
                solicitacao.getEstoqueDestino(), solicitacao.getAlmoxarifadoAtendimento(),
                solicitacao.getIdEquipeSaque(), solicitacao.getDataPrevistaRetirada(), solicitacao.getObservacao());

        assertThat(solicitacao.getOcorrencia()).isEqualTo("OC-1042");
        assertThat(operacao.getOcorrencia()).isNull();
        assertThat(movimentos).hasSize(2).allSatisfy(movimento -> {
            assertThat(movimento.getSolicitacao()).isSameAs(solicitacao);
            assertThat(movimento.getOcorrencia()).isNull();
            assertThat(movimento.getOperacaoSolicitacao()).isSameAs(operacao);
            assertThat(movimento.getItemOperacao()).isNotNull();
        });
    }

    @Test
    void ordenaLocksPorMaterialEPorSerieIndependentementeDaOrdemDoPayload() {
        UUID materialMenor = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID materialMaior = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

        assertThat(OperacaoSolicitacaoTecnicaUseCases.ordenarMateriaisParaLock(
                List.of(materialMaior, materialMenor)))
                .containsExactly(materialMenor, materialMaior);

        var serieB = new OperacaoSolicitacaoTecnicaUseCases.ChaveLockSerie(materialMenor, "SERIE-B");
        var serieA = new OperacaoSolicitacaoTecnicaUseCases.ChaveLockSerie(materialMenor, "SERIE-A");
        var serieOutroMaterial = new OperacaoSolicitacaoTecnicaUseCases.ChaveLockSerie(
                materialMaior, "SERIE-A");
        assertThat(OperacaoSolicitacaoTecnicaUseCases.ordenarSeriesParaLock(
                List.of(serieOutroMaterial, serieB, serieA)))
                .containsExactly(serieA, serieB, serieOutroMaterial);
    }

    private static ConfirmarEntregaTecnicaRequest request(SolicitacaoMaterial solicitacao, UUID idEquipe) {
        return new ConfirmarEntregaTecnicaRequest(
                0L, idEquipe, "assinatura almox", "assinatura equipe",
                List.of(new ConfirmarEntregaTecnicaRequest.ItemEntregaTecnicaRequest(
                        solicitacao.getItens().getFirst().getId(), new BigDecimal("2.000"), null)));
    }

    private void prepararPersistenciaOperacao() {
        when(operacaoRepository.saveAndFlush(any())).thenAnswer(invocacao -> {
            OperacaoSolicitacaoMaterial operacao = invocacao.getArgument(0);
            ReflectionTestUtils.setField(operacao, "id", UUID.randomUUID());
            operacao.getItens().forEach(item -> ReflectionTestUtils.setField(item, "id", UUID.randomUUID()));
            return operacao;
        });
    }

    private static OperacaoSolicitacaoMaterial operacaoOriginal(
            SolicitacaoMaterial solicitacao,
            TipoOperacaoSolicitacao tipo,
            BigDecimal quantidade
    ) {
        OperacaoSolicitacaoMaterial operacao = new OperacaoSolicitacaoMaterial(
                solicitacao, tipo, solicitacao.getIdEquipeSaque(), UUID.randomUUID(),
                null,
                tipo == TipoOperacaoSolicitacao.ENTREGA ? "assinatura-almox" : null,
                tipo == TipoOperacaoSolicitacao.ENTREGA ? "assinatura-equipe" : null,
                OrigemComandoTecnico.UI, null);
        operacao.adicionarItem(solicitacao.getItens().getFirst(), quantidade, null, null);
        ReflectionTestUtils.setField(operacao, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(operacao.getItens().getFirst(), "id", UUID.randomUUID());
        return operacao;
    }

    private static EstornarOperacaoTecnicaRequest estornoRequest(
            SolicitacaoMaterial solicitacao,
            OperacaoSolicitacaoMaterial original,
            BigDecimal quantidade
    ) {
        return new EstornarOperacaoTecnicaRequest(
                solicitacao.getVersao(), original.getId(), "Material devolvido", List.of(
                new EstornarOperacaoTecnicaRequest.ItemEstornoTecnicoRequest(
                        original.getItens().getFirst().getId(), quantidade, null)));
    }

    @Test
    void entregaTecnicaUtilizaColaboradorPlanejadoQuandoNaoInformadoNoRequest() {
        SolicitacaoMaterial solicitacao = solicitacaoPronta(true);
        UUID idColaboradorPlanejado = UUID.randomUUID();
        ReflectionTestUtils.setField(solicitacao, "idColaboradorSaque", idColaboradorPlanejado);
        ReflectionTestUtils.setField(solicitacao, "saqueForaEquipe", false);

        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(operacaoRepository.saveAndFlush(any())).thenAnswer(invocacao -> {
            OperacaoSolicitacaoMaterial operacao = invocacao.getArgument(0);
            ReflectionTestUtils.setField(operacao, "id", UUID.randomUUID());
            operacao.getItens().forEach(item -> ReflectionTestUtils.setField(item, "id", UUID.randomUUID()));
            return operacao;
        });

        ConfirmarEntregaTecnicaRequest request = request(solicitacao, UUID.randomUUID());
        useCases.entregar(solicitacao.getId(), request, UUID.randomUUID(), OrigemComandoTecnico.UI);

        ArgumentCaptor<OperacaoSolicitacaoMaterial> operacaoCaptor = ArgumentCaptor.forClass(OperacaoSolicitacaoMaterial.class);
        verify(operacaoRepository).saveAndFlush(operacaoCaptor.capture());
        assertThat(operacaoCaptor.getValue().getIdColaboradorSaque()).isEqualTo(idColaboradorPlanejado);
        assertThat(operacaoCaptor.getValue().isSaqueForaEquipe()).isFalse();

        verify(referenciaValidator).validar(
                solicitacao.getIdObra(), solicitacao.getOcorrencia(), request.idEquipe(), null,
                idColaboradorPlanejado, false);
    }

    @Test
    void entregaTecnicaComOverrideDeColaboradorSaqueRegistraNaOperacao() {
        SolicitacaoMaterial solicitacao = solicitacaoPronta(true);
        UUID idColaboradorPlanejado = UUID.randomUUID();
        UUID idColaboradorOverride = UUID.randomUUID();
        ReflectionTestUtils.setField(solicitacao, "idColaboradorSaque", idColaboradorPlanejado);
        ReflectionTestUtils.setField(solicitacao, "saqueForaEquipe", false);

        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(operacaoRepository.saveAndFlush(any())).thenAnswer(invocacao -> {
            OperacaoSolicitacaoMaterial operacao = invocacao.getArgument(0);
            ReflectionTestUtils.setField(operacao, "id", UUID.randomUUID());
            operacao.getItens().forEach(item -> ReflectionTestUtils.setField(item, "id", UUID.randomUUID()));
            return operacao;
        });

        UUID idItem = solicitacao.getItens().getFirst().getId();
        UUID idEquipe = UUID.randomUUID();
        ConfirmarEntregaTecnicaRequest request = new ConfirmarEntregaTecnicaRequest(
                solicitacao.getVersao(), idEquipe, idColaboradorOverride, true,
                "assinatura-almox", "assinatura-equipe",
                List.of(new ConfirmarEntregaTecnicaRequest.ItemEntregaTecnicaRequest(
                        idItem, new BigDecimal("2.000"), null)));

        useCases.entregar(solicitacao.getId(), request, UUID.randomUUID(), OrigemComandoTecnico.UI);

        ArgumentCaptor<OperacaoSolicitacaoMaterial> operacaoCaptor = ArgumentCaptor.forClass(OperacaoSolicitacaoMaterial.class);
        verify(operacaoRepository).saveAndFlush(operacaoCaptor.capture());
        assertThat(operacaoCaptor.getValue().getIdColaboradorSaque()).isEqualTo(idColaboradorOverride);
        assertThat(operacaoCaptor.getValue().isSaqueForaEquipe()).isTrue();

        verify(referenciaValidator).validar(
                solicitacao.getIdObra(), solicitacao.getOcorrencia(), idEquipe, null,
                idColaboradorOverride, true);
    }

    @Test
    void entregaTecnicaComReservaInformaReservaERecarregaSemConflitoDeVersao() {
        SolicitacaoMaterial solicitacao = solicitacaoPronta(false);
        UUID idUsuario = UUID.randomUUID();
        UUID idItem = solicitacao.getItens().getFirst().getId();
        UUID idEquipe = UUID.randomUUID();
        UUID idColaborador = UUID.randomUUID();
        ReflectionTestUtils.setField(solicitacao, "idColaboradorSaque", idColaborador);

        ConfirmarEntregaTecnicaRequest request = new ConfirmarEntregaTecnicaRequest(
                solicitacao.getVersao(), idEquipe, idColaborador, false,
                "assinatura-almox", "assinatura-equipe",
                List.of(new ConfirmarEntregaTecnicaRequest.ItemEntregaTecnicaRequest(
                        idItem, new BigDecimal("2.000"), null)),
                "1234567890", LocalDate.of(2026, 9, 18), null);

        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(operacaoRepository.saveAndFlush(any())).thenAnswer(invocacao -> {
            OperacaoSolicitacaoMaterial operacao = invocacao.getArgument(0);
            ReflectionTestUtils.setField(operacao, "id", UUID.randomUUID());
            operacao.getItens().forEach(item -> ReflectionTestUtils.setField(item, "id", UUID.randomUUID()));
            return operacao;
        });

        ReservaSolicitacaoTecnica reservaCriada = new ReservaSolicitacaoTecnica(
                solicitacao, new BigInteger("1234567890"), LocalDate.of(2026, 9, 18), null,
                null, null, idUsuario);
        ReflectionTestUtils.setField(solicitacao, "reserva", null);
        when(reservaRepository.findBySolicitacaoId(solicitacao.getId()))
                .thenReturn(Optional.of(reservaCriada));

        UUID idOperacao = useCases.entregar(solicitacao.getId(), request, idUsuario, OrigemComandoTecnico.UI);

        assertThat(idOperacao).isNotNull();
        verify(reservaUseCases).registrarOuAlterar(
                org.mockito.ArgumentMatchers.eq(solicitacao.getId()),
                any(),
                org.mockito.ArgumentMatchers.eq(idUsuario),
                org.mockito.ArgumentMatchers.eq(OrigemComandoTecnico.UI));
        assertThat(solicitacao.getReserva()).isNotNull();
        assertThat(solicitacao.getStatus()).isEqualTo(SolicitacaoMaterial.StatusSolicitacao.ENTREGUE);
    }

    @Test
    void entregaTecnicaEmObraComOCPermiteEntregaSemReserva() {
        SolicitacaoMaterial solicitacao = solicitacaoPronta(false);
        ReflectionTestUtils.setField(solicitacao, "ocorrencia", "OC-12345");
        UUID idUsuario = UUID.randomUUID();
        ConfirmarEntregaTecnicaRequest request = request(solicitacao, UUID.randomUUID());
        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(operacaoRepository.saveAndFlush(any())).thenAnswer(invocacao -> {
            OperacaoSolicitacaoMaterial operacao = invocacao.getArgument(0);
            ReflectionTestUtils.setField(operacao, "id", UUID.randomUUID());
            operacao.getItens().forEach(item -> ReflectionTestUtils.setField(item, "id", UUID.randomUUID()));
            return operacao;
        });
        when(saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(any(), any(), any()))
                .thenReturn(Optional.empty());

        UUID idOperacao = useCases.entregar(solicitacao.getId(), request, idUsuario, OrigemComandoTecnico.UI);

        assertThat(idOperacao).isNotNull();
        assertThat(solicitacao.getStatus()).isEqualTo(SolicitacaoMaterial.StatusSolicitacao.ENTREGUE);
    }

    @Test
    void entregaTecnicaEmObraComumSemReservaLancaInvarianteViolada() {
        SolicitacaoMaterial solicitacao = solicitacaoPronta(false);
        UUID idUsuario = UUID.randomUUID();
        ConfirmarEntregaTecnicaRequest request = request(solicitacao, UUID.randomUUID());
        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));

        assertThatThrownBy(() -> useCases.entregar(solicitacao.getId(), request, idUsuario, OrigemComandoTecnico.UI))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("Registre uma reserva valida antes de confirmar a movimentacao tecnica");
    }

    @Test
    void entregaTecnicaAposEstornoTotalComSaldoPendenteSucesso() {
        SolicitacaoMaterial solicitacao = solicitacaoPronta(true);
        UUID idUsuario = UUID.randomUUID();
        ItemSolicitacao item = solicitacao.getItens().getFirst();
        item.registrarEntregaTecnica(new BigDecimal("2.000"));
        solicitacao.confirmarEntregaTecnica();
        item.registrarEstornoEntregaTecnica(new BigDecimal("2.000"));
        solicitacao.confirmarEstornoTecnico();
        assertThat(solicitacao.getStatus()).isEqualTo(SolicitacaoMaterial.StatusSolicitacao.ESTORNADO);
        assertThat(item.getQuantidadePendente()).isEqualByComparingTo("2.000");

        ConfirmarEntregaTecnicaRequest request = request(solicitacao, UUID.randomUUID());
        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(operacaoRepository.saveAndFlush(any())).thenAnswer(invocacao -> {
            OperacaoSolicitacaoMaterial operacao = invocacao.getArgument(0);
            ReflectionTestUtils.setField(operacao, "id", UUID.randomUUID());
            operacao.getItens().forEach(it -> ReflectionTestUtils.setField(it, "id", UUID.randomUUID()));
            return operacao;
        });
        when(saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(any(), any(), any()))
                .thenReturn(Optional.empty());

        UUID idOperacao = useCases.entregar(solicitacao.getId(), request, idUsuario, OrigemComandoTecnico.UI);

        assertThat(idOperacao).isNotNull();
        assertThat(solicitacao.getStatus()).isEqualTo(SolicitacaoMaterial.StatusSolicitacao.ENTREGUE);
        assertThat(item.getQuantidadePendente()).isZero();
    }

    private static SolicitacaoMaterial solicitacaoPronta(boolean comReserva) {
        return solicitacaoPronta(comReserva, TipoSolicitacaoTecnica.ADICAO_COMUM);
    }

    private static SolicitacaoMaterial solicitacaoPronta(boolean comReserva, TipoSolicitacaoTecnica tipo) {
        return solicitacaoPronta(comReserva, tipo, false);
    }

    private static SolicitacaoMaterial solicitacaoPronta(
            boolean comReserva,
            TipoSolicitacaoTecnica tipo,
            boolean seriado
    ) {
        TipoEstoque tipoObra = comId(new TipoEstoque("OBRA"));
        TipoEstoque tipoFisico = comId(new TipoEstoque("FISICO"));
        Estoque almoxarifado = comId(new Estoque("ALMOXARIFADO", tipoFisico));
        Estoque estoqueObra = comId(new Estoque("ESTOQUE OBRA", tipoObra));
        ObraPublica obra = new ObraPublica(
                UUID.randomUUID(), "001", "Obra teste", TipoObraPublico.OBRA, true,
                UUID.randomUUID(), "OBRAS", true, UUID.randomUUID(), Set.of(), null, List.of());
        estoqueObra.vincularObra(obra.id());
        SolicitacaoMaterial solicitacao = SolicitacaoMaterial.criarTecnica(
                obra, null, tipo, estoqueObra, almoxarifado,
                UUID.randomUUID(), UUID.randomUUID(), false, LocalDate.now(), null, UUID.randomUUID());
        ReflectionTestUtils.setField(solicitacao, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(solicitacao, "versao", 0L);
        solicitacao.adicionarItemTecnico(comId(new com.nexo.app.modules.estoque.domain.Material(
                "MAT-ENTREGA", "Material de entrega", "UN", "MATR", null, null,
                seriado, UUID.randomUUID())),
                seriado ? BigDecimal.ONE : new BigDecimal("2.000"));
        ReflectionTestUtils.setField(solicitacao.getItens().getFirst(), "id", UUID.randomUUID());
        if (tipo.isAdicao()) {
            solicitacao.iniciarSeparacao(UUID.randomUUID());
            solicitacao.concluirSeparacao(UUID.randomUUID());
            assertThat(solicitacao.getStatusSeparacao()).isEqualTo(StatusSeparacao.SEPARADO);
        }
        if (comReserva) {
            new ReservaSolicitacaoTecnica(
                    solicitacao, BigInteger.ONE, LocalDate.of(2026, 8, 1), UUID.randomUUID(),
                    "Agente responsavel", "Concessionaria", UUID.randomUUID());
        }
        return solicitacao;
    }

    private static <T extends BaseEntity> T comId(T entidade) {
        ReflectionTestUtils.setField(entidade, "id", UUID.randomUUID());
        return entidade;
    }
}
