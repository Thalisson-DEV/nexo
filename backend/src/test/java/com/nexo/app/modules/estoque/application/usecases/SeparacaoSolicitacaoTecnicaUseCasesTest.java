package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.services.GravadorEventoSolicitacaoService;
import com.nexo.app.modules.estoque.application.services.ObservabilidadeSolicitacaoTecnica;
import com.nexo.app.modules.estoque.application.services.PublicadorEventoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.OrigemComandoTecnico;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.StatusSeparacao;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.modules.estoque.domain.TipoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.ComandoIdempotenteSolicitacaoRepository;
import com.nexo.app.modules.obras.ObraApi.ObraPublica;
import com.nexo.app.modules.obras.ObraApi.TipoObraPublico;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.ConflitoVersaoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class SeparacaoSolicitacaoTecnicaUseCasesTest {

    @Mock private SolicitacaoMaterialRepository solicitacaoRepository;
    @Mock private ComandoIdempotenteSolicitacaoRepository comandoRepository;
    @Mock private GravadorEventoSolicitacaoService gravadorEvento;
    @Mock private PublicadorEventoSolicitacaoTecnica publicadorEventos;
    @Mock private ObservabilidadeSolicitacaoTecnica observabilidade;
    @InjectMocks private SeparacaoSolicitacaoTecnicaUseCases useCases;

    @Test
    void iniciaEConcluiSomenteAdicaoTecnicaComAutorEInstante() {
        SolicitacaoMaterial solicitacao = adicaoComData();
        UUID usuarioInicio = UUID.randomUUID();
        UUID usuarioConclusao = UUID.randomUUID();
        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));

        assertThat(useCases.iniciar(solicitacao.getId(), solicitacao.getVersao(), usuarioInicio, "inicio-1"))
                .isEqualTo(StatusSeparacao.EM_SEPARACAO);
        assertThat(solicitacao.getIdUsuarioInicioSeparacao()).isEqualTo(usuarioInicio);
        assertThat(solicitacao.getSeparacaoIniciadaEm()).isNotNull();
        assertThat(useCases.concluir(solicitacao.getId(), solicitacao.getVersao(), usuarioConclusao, "conclusao-1"))
                .isEqualTo(StatusSeparacao.SEPARADO);
        assertThat(solicitacao.getIdUsuarioConclusaoSeparacao()).isEqualTo(usuarioConclusao);
        assertThat(solicitacao.getSeparacaoConcluidaEm()).isNotNull();
        verify(gravadorEvento).registrarFilho(
                same(solicitacao), eq(TipoEventoSolicitacao.SEPARACAO_INICIADA), any(), any());
        verify(gravadorEvento).registrarFilho(
                same(solicitacao), eq(TipoEventoSolicitacao.SEPARACAO_CONCLUIDA), any(), any());
        verify(publicadorEventos).publicarFilho(
                com.nexo.app.shared.domain.events.SolicitacaoMaterialTecnicaEvent.Tipo.ADICAO_SEPARADA,
                solicitacao);
    }

    @Test
    void pulaSeparacaoDeAdicaoTecnicaDiretoParaSeparado() {
        SolicitacaoMaterial solicitacao = adicaoComData();
        UUID usuarioPulo = UUID.randomUUID();
        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));

        assertThat(useCases.pular(solicitacao.getId(), solicitacao.getVersao(), usuarioPulo, "pular-1"))
                .isEqualTo(StatusSeparacao.SEPARADO);
        assertThat(solicitacao.getIdUsuarioInicioSeparacao()).isEqualTo(usuarioPulo);
        assertThat(solicitacao.getSeparacaoIniciadaEm()).isNotNull();
        assertThat(solicitacao.getIdUsuarioConclusaoSeparacao()).isEqualTo(usuarioPulo);
        assertThat(solicitacao.getSeparacaoConcluidaEm()).isNotNull();
        verify(gravadorEvento).registrarFilho(
                same(solicitacao), eq(TipoEventoSolicitacao.SEPARACAO_CONCLUIDA), any(), any());
        verify(publicadorEventos).publicarFilho(
                com.nexo.app.shared.domain.events.SolicitacaoMaterialTecnicaEvent.Tipo.ADICAO_SEPARADA,
                solicitacao);
    }

    @Test
    void rejeitaSeparacaoDeDevolucao() {
        SolicitacaoMaterial devolucao = adicaoComData();
        ReflectionTestUtils.setField(devolucao, "tipoTecnico", TipoSolicitacaoTecnica.DEVOLUCAO_COMUM);
        ReflectionTestUtils.setField(devolucao, "statusSeparacao", null);
        when(solicitacaoRepository.findById(devolucao.getId())).thenReturn(Optional.of(devolucao));

        assertThatThrownBy(() -> useCases.iniciar(
                devolucao.getId(), devolucao.getVersao(), UUID.randomUUID(), "devolucao-1"))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("somente a adicoes tecnicas");
        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    void rejeitaTransicaoForaDaOrdem() {
        SolicitacaoMaterial solicitacao = adicaoComData();
        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));

        assertThatThrownBy(() -> useCases.concluir(
                solicitacao.getId(), solicitacao.getVersao(), UUID.randomUUID(), "fora-ordem-1"))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("em andamento");
        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    void rejeitaVersaoObsoletaComVersaoAtualParaRecarga() {
        SolicitacaoMaterial solicitacao = adicaoComData();
        Long versaoEsperada = solicitacao.getVersao() - 1;
        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));

        assertThatThrownBy(() -> useCases.iniciar(
                solicitacao.getId(), versaoEsperada, UUID.randomUUID(), "versao-obsoleta"))
                .isInstanceOfSatisfying(ConflitoVersaoException.class, ex -> {
                    assertThat(ex.getVersaoEsperada()).isEqualTo(versaoEsperada);
                    assertThat(ex.getVersaoAtual()).isEqualTo(solicitacao.getVersao());
                });

        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    void reenvioComMesmaChaveRetornaResultadoOriginalSemNovoEvento() {
        SolicitacaoMaterial solicitacao = adicaoComData();
        UUID usuario = UUID.randomUUID();
        AtomicReference<com.nexo.app.modules.estoque.domain.ComandoIdempotenteSolicitacao> comando =
                new AtomicReference<>();
        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(comandoRepository.findByEscopoAndIdUsuarioAndChave("SEPARACAO_INICIAR", usuario, "reenvio-1"))
                .thenAnswer(ignorado -> Optional.ofNullable(comando.get()));
        when(comandoRepository.save(any())).thenAnswer(invocacao -> {
            var salvo = invocacao.getArgument(0,
                    com.nexo.app.modules.estoque.domain.ComandoIdempotenteSolicitacao.class);
            comando.set(salvo);
            return salvo;
        });

        useCases.iniciar(solicitacao.getId(), solicitacao.getVersao(), usuario, "reenvio-1");
        StatusSeparacao repetida = useCases.iniciar(
                solicitacao.getId(), solicitacao.getVersao(), usuario, "reenvio-1");

        assertThat(repetida).isEqualTo(StatusSeparacao.EM_SEPARACAO);
        verify(solicitacaoRepository, times(1)).save(solicitacao);
        verify(gravadorEvento, times(1)).registrarFilho(
                same(solicitacao), eq(TipoEventoSolicitacao.SEPARACAO_INICIADA), any(), any());
    }

    @Test
    void rejeitaChaveReutilizadaComPayloadDivergente() {
        SolicitacaoMaterial solicitacao = adicaoComData();
        UUID usuario = UUID.randomUUID();
        AtomicReference<com.nexo.app.modules.estoque.domain.ComandoIdempotenteSolicitacao> comando =
                new AtomicReference<>();
        when(solicitacaoRepository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));
        when(comandoRepository.findByEscopoAndIdUsuarioAndChave("SEPARACAO_INICIAR", usuario, "divergente-1"))
                .thenAnswer(ignorado -> Optional.ofNullable(comando.get()));
        when(comandoRepository.save(any())).thenAnswer(invocacao -> {
            var salvo = invocacao.getArgument(0,
                    com.nexo.app.modules.estoque.domain.ComandoIdempotenteSolicitacao.class);
            comando.set(salvo);
            return salvo;
        });

        useCases.iniciar(solicitacao.getId(), solicitacao.getVersao(), usuario, "divergente-1");

        assertThatThrownBy(() -> useCases.iniciar(
                solicitacao.getId(), solicitacao.getVersao() + 1, usuario, "divergente-1"))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("outro payload");
        verify(solicitacaoRepository, times(1)).save(solicitacao);
    }

    @Test
    void encerramentoPreservaQuantidadesEImpedeNovaSeparacao() {
        SolicitacaoMaterial solicitacao = adicaoComData();
        Material material = comId(new Material("MAT", "Material", "UN", "MATR", null, null,
                false, UUID.randomUUID()));
        solicitacao.adicionarItemTecnico(material, new BigDecimal("2.000"));
        solicitacao.getItens().getFirst().registrarEntregaTecnica(BigDecimal.ONE);

        solicitacao.encerrarSaldoPendente(UUID.randomUUID(), "Material restante não será utilizado");

        assertThat(solicitacao.estaEncerrada()).isTrue();
        assertThat(solicitacao.getItens().getFirst().getQuantidadePendente()).isEqualByComparingTo("1.000");
        assertThatThrownBy(() -> solicitacao.iniciarSeparacao(UUID.randomUUID()))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("saldo pendente encerrado");
    }

    @Test
    void rejeitaSeparacaoComercial() {
        SolicitacaoMaterial comercial = solicitacaoComercialAprovada();
        UUID usuario = UUID.randomUUID();
        when(solicitacaoRepository.findById(comercial.getId())).thenReturn(Optional.of(comercial));

        assertThatThrownBy(() -> useCases.iniciar(comercial.getId(), comercial.getVersao(), usuario, "comercial-ini-1"))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("solicitacoes tecnicas");

        assertThatThrownBy(() -> useCases.pular(comercial.getId(), comercial.getVersao(), usuario, "comercial-pular-1"))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("solicitacoes tecnicas");
    }

    private static SolicitacaoMaterial solicitacaoComercialAprovada() {
        Estoque almoxarifado = comId(new Estoque("ALMOX", comId(new TipoEstoque("FISICO"))));
        Estoque obra = comId(new Estoque("OBRA", comId(new TipoEstoque("CAMPO"))));
        SolicitacaoMaterial solicitacao = comId(new SolicitacaoMaterial(almoxarifado, obra, UUID.randomUUID()));
        ReflectionTestUtils.setField(solicitacao, "contexto", com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial.COMERCIAL);
        ReflectionTestUtils.setField(solicitacao, "status", SolicitacaoMaterial.StatusSolicitacao.APROVADO);
        ReflectionTestUtils.setField(solicitacao, "statusSeparacao", StatusSeparacao.PRONTO_PARA_SEPARACAO);
        ReflectionTestUtils.setField(solicitacao, "versao", 1L);
        return solicitacao;
    }

    private static SolicitacaoMaterial adicaoComData() {
        ObraPublica obra = new ObraPublica(
                UUID.randomUUID(), "001", "Obra", TipoObraPublico.OBRA, true,
                UUID.randomUUID(), "OBRAS", true, UUID.randomUUID(), Set.of(), null, List.of());
        Estoque almoxarifado = comId(new Estoque("ALMOX", comId(new TipoEstoque("FISICO"))));
        almoxarifado.vincularBaseOperacional(obra.idBaseOperacional());
        Estoque estoqueObra = comId(new Estoque("OBRA", comId(new TipoEstoque("OBRA"))));
        estoqueObra.vincularBaseOperacional(obra.idBaseOperacional());
        SolicitacaoMaterial solicitacao = comId(SolicitacaoMaterial.criarTecnica(
                obra, null, TipoSolicitacaoTecnica.ADICAO_COMUM, estoqueObra, almoxarifado,
                null, LocalDate.now().plusDays(1), null, UUID.randomUUID()));
        ReflectionTestUtils.setField(solicitacao, "versao", 2L);
        return solicitacao;
    }

    private static <T extends BaseEntity> T comId(T entidade) {
        ReflectionTestUtils.setField(entidade, "id", UUID.randomUUID());
        return entidade;
    }
}
