package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.EncerrarSaldoTecnicoRequest;
import com.nexo.app.modules.estoque.application.dto.CriarSolicitacaoTecnicaRequest;
import com.nexo.app.modules.estoque.application.dto.ConfirmarEntregaTecnicaRequest;
import com.nexo.app.modules.estoque.application.dto.RegistrarReservaTecnicaRequest;
import com.nexo.app.modules.estoque.application.services.GravadorEventoSolicitacaoService;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.OperacaoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.OrigemComandoTecnico;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.modules.estoque.domain.TipoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.TipoOperacaoSolicitacao;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.infrastructure.persistence.ComandoIdempotenteSolicitacaoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.TipoEstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MovimentacaoEstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.OperacaoSolicitacaoMaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.ReservaSolicitacaoTecnicaRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoRepository;
import com.nexo.app.shared.domain.exception.ConflitoVersaoException;
import com.nexo.app.modules.obras.AgenteResponsavelApi.AgenteResponsavelPublico;
import com.nexo.app.modules.obras.ObraApi.ObraPublica;
import com.nexo.app.modules.obras.ObraApi.TipoObraPublico;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Verifica que fatos operacionais e a linha do tempo permanecem uma unica transacao. */
@SpringBootTest
class EncerramentoSaldoTecnicoTransactionalTest {

    @Autowired private EncerramentoSaldoTecnicoUseCases useCases;
    @Autowired private SolicitacaoTecnicaUseCases solicitacaoTecnicaUseCases;
    @Autowired private ReservaSolicitacaoTecnicaUseCases reservaUseCases;
    @Autowired private OperacaoSolicitacaoTecnicaUseCases operacaoUseCases;
    @Autowired private TipoEstoqueRepository tipoEstoqueRepository;
    @Autowired private EstoqueRepository estoqueRepository;
    @Autowired private MaterialRepository materialRepository;
    @Autowired private ComandoIdempotenteSolicitacaoRepository comandoRepository;
    @Autowired private SolicitacaoMaterialRepository solicitacaoRepository;
    @Autowired private ReservaSolicitacaoTecnicaRepository reservaRepository;
    @Autowired private OperacaoSolicitacaoMaterialRepository operacaoRepository;
    @Autowired private MovimentacaoEstoqueRepository movimentacaoRepository;
    @Autowired private SaldoRepository saldoRepository;

    @MockitoBean private GravadorEventoSolicitacaoService gravadorEvento;
    @MockitoBean private ReferenciaTecnicaValidator referenciaValidator;
    @MockitoBean private EstoqueObraProvisioningService estoqueObraProvisioningService;
    @MockitoBean private EstoqueCampoEquipeProvisioningService estoqueCampoEquipeProvisioningService;

    @Test
    void criacaoIndependenteRepeteRespostaOriginalERejeitaPayloadDivergente() {
        DadosTecnicos dados = dadosTecnicos();
        UUID idUsuario = UUID.randomUUID();
        String chave = "criacao-" + UUID.randomUUID();
        CriarSolicitacaoTecnicaRequest request = new CriarSolicitacaoTecnicaRequest(
                dados.obra().id(), null, dados.almoxarifado().getId(), null,
                LocalDate.now(), "Criacao idempotente", List.of(
                new CriarSolicitacaoTecnicaRequest.Item(
                        dados.material().getId(), TipoSolicitacaoTecnica.ADICAO_COMUM,
                        new BigDecimal("2.000"))));
        when(referenciaValidator.validar(dados.obra().id(), null, null, null, null, false))
                .thenReturn(new ReferenciaTecnicaValidator.ContextoTecnicoValidado(
                        dados.obra(), null, null, null));
        when(estoqueObraProvisioningService.resolverOuProvisionar(dados.obra()))
                .thenReturn(dados.estoqueObra());
        long solicitacoesAntes = solicitacaoRepository.count();

        var primeira = solicitacaoTecnicaUseCases.criar(
                request, idUsuario, OrigemComandoTecnico.UI, chave);
        clearInvocations(gravadorEvento);
        var repetida = solicitacaoTecnicaUseCases.criar(
                request, idUsuario, OrigemComandoTecnico.UI, chave);

        assertThat(repetida).isEqualTo(primeira);
        assertThat(solicitacaoRepository.count()).isEqualTo(solicitacoesAntes + 1);
        var comando = comandoRepository.findByEscopoAndIdUsuarioAndChave(
                        "CRIACAO_SOLICITACAO_TECNICA", idUsuario, chave)
                .orElseThrow();
        assertThat(comando.getIdSolicitacao()).isNotNull();
        assertThat(comando.getIdOperacao()).isNull();

        CriarSolicitacaoTecnicaRequest divergente = new CriarSolicitacaoTecnicaRequest(
                request.idObra(), null, request.idAlmoxarifadoAtendimento(), null,
                request.dataPrevista(), "Outro conteudo", request.itens());
        assertThatThrownBy(() -> solicitacaoTecnicaUseCases.criar(
                divergente, idUsuario, OrigemComandoTecnico.UI, chave))
                .isInstanceOf(com.nexo.app.shared.domain.exception.InvarianteVioladaException.class)
                .hasMessageContaining("outro payload");
        verifyNoInteractions(gravadorEvento);
    }

    @Test
    void reservaRepeteSnapshotAntesDaVersaoSemDuplicarEvento() {
        SolicitacaoMaterial solicitacao = solicitacaoTecnicaPersistida(false, false);
        UUID idUsuario = UUID.randomUUID();
        UUID idAgente = UUID.randomUUID();
        String chave = "reserva-" + UUID.randomUUID();
        RegistrarReservaTecnicaRequest request = new RegistrarReservaTecnicaRequest(
                solicitacao.getVersao(), "123456", LocalDate.now(), idAgente, null);
        when(referenciaValidator.validarAgente(idAgente)).thenReturn(
                new AgenteResponsavelPublico(
                        idAgente, "Agente teste", null, null, "Concessionaria"));
        long reservasAntes = reservaRepository.count();

        var primeira = reservaUseCases.registrarOuAlterar(
                solicitacao.getId(), request, idUsuario, OrigemComandoTecnico.UI, chave);
        clearInvocations(gravadorEvento);
        var repetida = reservaUseCases.registrarOuAlterar(
                solicitacao.getId(), request, idUsuario, OrigemComandoTecnico.UI, chave);

        assertThat(repetida).isEqualTo(primeira);
        assertThat(reservaRepository.count()).isEqualTo(reservasAntes + 1);
        var comando = comandoRepository.findByEscopoAndIdUsuarioAndChave(
                        "RESERVA_TECNICA:" + solicitacao.getId(), idUsuario, chave)
                .orElseThrow();
        assertThat(comando.getIdSolicitacao()).isEqualTo(solicitacao.getId());
        assertThat(comando.getIdOperacao()).isNull();

        RegistrarReservaTecnicaRequest divergente = new RegistrarReservaTecnicaRequest(
                request.versaoEsperada(), "654321", request.dataCriacao(), idAgente, null);
        assertThatThrownBy(() -> reservaUseCases.registrarOuAlterar(
                solicitacao.getId(), divergente, idUsuario, OrigemComandoTecnico.UI, chave))
                .isInstanceOf(com.nexo.app.shared.domain.exception.InvarianteVioladaException.class)
                .hasMessageContaining("outro payload");
        verifyNoInteractions(gravadorEvento);
    }

    @Test
    void encerramentoCriaOperacaoImutavelEReplayNaoDuplicaFatos() {
        SolicitacaoMaterial solicitacao = solicitacaoParcialPersistida();
        UUID idUsuario = UUID.randomUUID();
        String chave = "encerramento-" + UUID.randomUUID();
        EncerrarSaldoTecnicoRequest request = new EncerrarSaldoTecnicoRequest(
                solicitacao.getVersao(), "Saldo nao utilizado");
        long operacoesAntes = operacaoRepository.count();

        useCases.encerrar(solicitacao.getId(), request, idUsuario, chave);
        clearInvocations(gravadorEvento);
        useCases.encerrar(solicitacao.getId(), request, idUsuario, chave);

        assertThat(operacaoRepository.count()).isEqualTo(operacoesAntes + 1);
        var comando = comandoRepository.findByEscopoAndIdUsuarioAndChave(
                        "ENCERRAMENTO_TECNICO:" + solicitacao.getId(), idUsuario, chave)
                .orElseThrow();
        assertThat(comando.getIdSolicitacao()).isEqualTo(solicitacao.getId());
        assertThat(comando.getIdOperacao()).isNotNull();
        OperacaoSolicitacaoMaterial operacao = operacaoRepository.findById(comando.getIdOperacao()).orElseThrow();
        assertThat(operacao.getTipo()).isEqualTo(TipoOperacaoSolicitacao.ENCERRAMENTO);
        assertThat(operacao.getMotivo()).isEqualTo(request.motivo());
        assertThat(operacao.getIdUsuarioOperador()).isEqualTo(idUsuario);
        assertThat(operacao.getTipoTecnico()).isEqualTo(solicitacao.getTipoTecnico());
        assertThat(operacao.getTipoObraSnapshot()).isEqualTo(solicitacao.getTipoObraSnapshot());
        assertThat(operacao.getIdObra()).isEqualTo(solicitacao.getIdObra());
        assertThat(operacao.getOperacaoOriginal()).isNull();
        assertThat(operacao.getReserva()).isNull();

        EncerrarSaldoTecnicoRequest divergente = new EncerrarSaldoTecnicoRequest(
                request.versaoEsperada(), "Outro motivo");
        assertThatThrownBy(() -> useCases.encerrar(
                solicitacao.getId(), divergente, idUsuario, chave))
                .isInstanceOf(com.nexo.app.shared.domain.exception.InvarianteVioladaException.class)
                .hasMessageContaining("outro payload");
        verifyNoInteractions(gravadorEvento);
    }

    @Test
    void encerramentoRejeitaVersaoObsoletaComVersaoAtual() {
        SolicitacaoMaterial solicitacao = solicitacaoParcialPersistida();
        EncerrarSaldoTecnicoRequest request = new EncerrarSaldoTecnicoRequest(
                solicitacao.getVersao() - 1, "Saldo nao utilizado");
        long operacoesAntes = operacaoRepository.count();

        assertThatThrownBy(() -> useCases.encerrar(
                solicitacao.getId(), request, UUID.randomUUID()))
                .isInstanceOfSatisfying(ConflitoVersaoException.class, ex -> {
                    assertThat(ex.getVersaoEsperada()).isEqualTo(request.versaoEsperada());
                    assertThat(ex.getVersaoAtual()).isEqualTo(solicitacao.getVersao());
                });

        assertThat(operacaoRepository.count()).isEqualTo(operacoesAntes);
    }

    @Test
    void reverteEncerramentoQuandoPersistenciaDoHistoricoFalha() {
        SolicitacaoMaterial solicitacao = solicitacaoParcialPersistida();
        doThrow(new IllegalStateException("Falha injetada no historico"))
                .when(gravadorEvento)
                .registrarFilho(any(), any(TipoEventoSolicitacao.class), any(), any());

        assertThatThrownBy(() -> useCases.encerrar(
                solicitacao.getId(),
                new EncerrarSaldoTecnicoRequest(solicitacao.getVersao(), "Saldo nao utilizado"),
                UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Falha injetada no historico");

        assertThat(solicitacaoRepository.findById(solicitacao.getId()))
                .get()
                .matches(persistida -> !persistida.estaEncerrada());
    }

    @Test
    void reverteSolicitacaoQuandoEventoInicialFalha() {
        DadosTecnicos dados = dadosTecnicos();
        long solicitacoesAntes = solicitacaoRepository.count();
        when(referenciaValidator.validar(dados.obra().id(), null, null, null, null, false))
                .thenReturn(new ReferenciaTecnicaValidator.ContextoTecnicoValidado(
                        dados.obra(), null, null, null));
        when(estoqueObraProvisioningService.resolverOuProvisionar(dados.obra()))
                .thenReturn(dados.estoqueObra());
        doThrow(new IllegalStateException("Falha injetada no historico"))
                .when(gravadorEvento)
                .registrarFilho(any(), any(TipoEventoSolicitacao.class), any(), any());

        assertThatThrownBy(() -> solicitacaoTecnicaUseCases.criar(
                new CriarSolicitacaoTecnicaRequest(
                        dados.obra().id(), null, dados.almoxarifado().getId(), null,
                        LocalDate.now(), null, List.of(new CriarSolicitacaoTecnicaRequest.Item(
                                dados.material().getId(), TipoSolicitacaoTecnica.ADICAO_COMUM,
                                new BigDecimal("2.000")))),
                UUID.randomUUID(), OrigemComandoTecnico.UI))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Falha injetada no historico");

        assertThat(solicitacaoRepository.count()).isEqualTo(solicitacoesAntes);
    }

    @Test
    void reverteReservaQuandoHistoricoFalha() {
        SolicitacaoMaterial solicitacao = solicitacaoTecnicaPersistida(false, false);
        UUID idAgente = UUID.randomUUID();
        when(referenciaValidator.validarAgente(idAgente)).thenReturn(
                new AgenteResponsavelPublico(idAgente, "Agente teste", null, null, "Concessionaria"));
        doThrow(new IllegalStateException("Falha injetada no historico"))
                .when(gravadorEvento)
                .registrarFilho(any(), any(TipoEventoSolicitacao.class), any(), any());

        assertThatThrownBy(() -> reservaUseCases.registrarOuAlterar(
                solicitacao.getId(), new RegistrarReservaTecnicaRequest(
                        solicitacao.getVersao(), "123456", LocalDate.now(), idAgente, null), UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(reservaRepository.findBySolicitacaoId(solicitacao.getId())).isEmpty();
    }

    @Test
    void reverteOperacaoSaldoEMovimentoQuandoHistoricoFalha() {
        SolicitacaoMaterial solicitacao = solicitacaoTecnicaPersistida(true, true);
        DadosTecnicos dados = new DadosTecnicos(
                new ObraPublica(solicitacao.getIdObra(), "001", "Obra de teste", TipoObraPublico.OBRA, true,
                        UUID.randomUUID(), "OBRAS", true, UUID.randomUUID(), Set.of(), null, List.of()),
                solicitacao.getAlmoxarifadoAtendimento(), solicitacao.getEstoqueDestino(),
                solicitacao.getItens().getFirst().getMaterial());
        UUID idEquipe = solicitacao.getIdEquipeSaque();
        UUID idColaborador = UUID.randomUUID();
        when(referenciaValidator.validar(solicitacao.getIdObra(), null, idEquipe, null, idColaborador, false))
                .thenReturn(new ReferenciaTecnicaValidator.ContextoTecnicoValidado(dados.obra(), null, null, null));
        TipoEstoque tipoCampo = tipoEstoqueRepository.findByNomeIgnoreCase("CAMPO")
                .orElseGet(() -> tipoEstoqueRepository.saveAndFlush(new TipoEstoque("CAMPO")));
        Estoque estoqueEquipe = new Estoque("EQUIPE " + UUID.randomUUID(), tipoCampo);
        estoqueEquipe.vincularEquipe(idEquipe);
        estoqueEquipe = estoqueRepository.saveAndFlush(estoqueEquipe);
        when(estoqueCampoEquipeProvisioningService.resolver(idEquipe)).thenReturn(estoqueEquipe);
        doThrow(new IllegalStateException("Falha injetada no historico"))
                .when(gravadorEvento)
                .registrarFilho(any(), any(TipoEventoSolicitacao.class), any(), any());
        long operacoesAntes = operacaoRepository.count();
        long movimentosAntes = movimentacaoRepository.count();
        long saldosAntes = saldoRepository.count();

        assertThatThrownBy(() -> operacaoUseCases.entregar(
                solicitacao.getId(), new ConfirmarEntregaTecnicaRequest(
                        solicitacao.getVersao(), idEquipe, idColaborador, false,
                        "assinatura-almox", "assinatura-equipe", List.of(
                        new ConfirmarEntregaTecnicaRequest.ItemEntregaTecnicaRequest(
                                solicitacao.getItens().getFirst().getId(), new BigDecimal("2.000"), null))),
                UUID.randomUUID(), OrigemComandoTecnico.UI))
                .isInstanceOf(IllegalStateException.class);

        assertThat(operacaoRepository.count()).isEqualTo(operacoesAntes);
        assertThat(movimentacaoRepository.count()).isEqualTo(movimentosAntes);
        assertThat(saldoRepository.count()).isEqualTo(saldosAntes);
        assertThat(solicitacaoRepository.findById(solicitacao.getId()))
                .get()
                .matches(persistida -> persistida.getStatus() == SolicitacaoMaterial.StatusSolicitacao.APROVADO);
    }

    private SolicitacaoMaterial solicitacaoParcialPersistida() {
        SolicitacaoMaterial solicitacao = solicitacaoTecnicaPersistida(false, false);
        solicitacao.getItens().getFirst().registrarEntregaTecnica(BigDecimal.ONE);
        solicitacao.confirmarEntregaTecnica();
        return solicitacaoRepository.saveAndFlush(solicitacao);
    }

    private SolicitacaoMaterial solicitacaoTecnicaPersistida(boolean comReserva, boolean separada) {
        DadosTecnicos dados = dadosTecnicos();
        UUID idEquipe = UUID.randomUUID();
        SolicitacaoMaterial solicitacao = SolicitacaoMaterial.criarTecnica(
                dados.obra(), null, TipoSolicitacaoTecnica.ADICAO_COMUM, dados.estoqueObra(), dados.almoxarifado(),
                idEquipe, LocalDate.now(), null, UUID.randomUUID());
        solicitacao.adicionarItemTecnico(dados.material(), new BigDecimal("2.000"));
        solicitacao = solicitacaoRepository.saveAndFlush(solicitacao);
        if (separada) {
            solicitacao.iniciarSeparacao(UUID.randomUUID());
            solicitacao.concluirSeparacao(UUID.randomUUID());
            solicitacao = solicitacaoRepository.saveAndFlush(solicitacao);
        }
        if (comReserva) {
            reservaRepository.saveAndFlush(new com.nexo.app.modules.estoque.domain.ReservaSolicitacaoTecnica(
                    solicitacao, new java.math.BigInteger("123456"), LocalDate.now(), UUID.randomUUID(),
                    "Agente teste", "Concessionaria", UUID.randomUUID()));
        }
        return solicitacao;
    }

    private DadosTecnicos dadosTecnicos() {
        String sufixo = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        TipoEstoque tipoFisico = tipoEstoqueRepository.findByNomeIgnoreCase("FISICO")
                .orElseGet(() -> tipoEstoqueRepository.saveAndFlush(new TipoEstoque("FISICO")));
        TipoEstoque tipoObra = tipoEstoqueRepository.findByNomeIgnoreCase("OBRA")
                .orElseGet(() -> tipoEstoqueRepository.saveAndFlush(new TipoEstoque("OBRA")));
        Estoque almoxarifado = estoqueRepository.saveAndFlush(new Estoque("ALMOX " + sufixo, tipoFisico));
        UUID idObra = UUID.randomUUID();
        Estoque estoqueObra = new Estoque("OBRA " + sufixo, tipoObra);
        estoqueObra.vincularObra(idObra);
        estoqueObra = estoqueRepository.saveAndFlush(estoqueObra);
        Material material = materialRepository.saveAndFlush(new Material(
                "MAT-" + sufixo, "Material tecnico", "UN", "MATR",
                null, null, false, UUID.randomUUID()));
        ObraPublica obra = new ObraPublica(
                idObra, "001" + sufixo, "Obra de teste", TipoObraPublico.OBRA, true,
                UUID.randomUUID(), "OBRAS", true, UUID.randomUUID(), Set.of(), null, List.of());
        return new DadosTecnicos(obra, almoxarifado, estoqueObra, material);
    }

    private record DadosTecnicos(ObraPublica obra, Estoque almoxarifado, Estoque estoqueObra, Material material) {
    }
}
