package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.RegistrarReservaTecnicaRequest;
import com.nexo.app.modules.estoque.application.services.GravadorEventoSolicitacaoService;
import com.nexo.app.modules.estoque.application.services.ObservabilidadeSolicitacaoTecnica;
import com.nexo.app.modules.estoque.application.services.PublicadorEventoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.domain.PayloadEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.TipoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.OrigemComandoTecnico;
import com.nexo.app.modules.estoque.domain.ReservaSolicitacaoTecnica;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.infrastructure.persistence.ReservaSolicitacaoTecnicaRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import com.nexo.app.modules.obras.AgenteResponsavelApi.AgenteResponsavelPublico;
import com.nexo.app.modules.obras.ObraApi.ObraPublica;
import com.nexo.app.modules.obras.ObraApi.TipoObraPublico;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.PreenchimentoInvalidoException;
import com.nexo.app.shared.domain.exception.ConflitoVersaoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ReservaSolicitacaoTecnicaUseCasesTest {

    @Mock private SolicitacaoMaterialRepository solicitacaoRepository;
    @Mock private ReservaSolicitacaoTecnicaRepository reservaRepository;
    @Mock private ReferenciaTecnicaValidator referenciaValidator;
    @Mock private GravadorEventoSolicitacaoService gravadorEvento;
    @Mock private PublicadorEventoSolicitacaoTecnica publicadorEventos;
    @Mock private ObservabilidadeSolicitacaoTecnica observabilidade;
    @InjectMocks private ReservaSolicitacaoTecnicaUseCases useCases;

    @Test
    void registraNumeroDeVinteDigitosComoStringSemPerdaEAgenteIndependenteDaObra() {
        Fixture f = fixture();
        UUID outroAgente = UUID.randomUUID();
        RegistrarReservaTecnicaRequest request = request(
                f, "99999999999999999999", outroAgente, null);
        prepararCriacao(f, request, agente(outroAgente));

        var resposta = useCases.registrarOuAlterar(f.filho().getId(), request, UUID.randomUUID());

        assertThat(resposta.numero()).isEqualTo("99999999999999999999");
        assertThat(resposta.idAgenteResponsavel()).isEqualTo(outroAgente);
        assertThat(resposta.dataCriacao()).isEqualTo(LocalDate.of(2026, 8, 1));
        verify(reservaRepository).save(any(ReservaSolicitacaoTecnica.class));
        verify(publicadorEventos).publicarFilho(
                com.nexo.app.shared.domain.events.SolicitacaoMaterialTecnicaEvent.Tipo.RESERVA_REGISTRADA,
                f.filho());
    }

    @Test
    void registraReservaSemAgenteResponsavel() {
        Fixture f = fixture();
        RegistrarReservaTecnicaRequest request = request(f, "123", null, null);
        prepararCriacao(f, request, null);

        var resposta = useCases.registrarOuAlterar(f.filho().getId(), request, UUID.randomUUID());

        assertThat(resposta.idAgenteResponsavel()).isNull();
        assertThat(resposta.nomeAgente()).isNull();
        verify(reservaRepository).save(any(ReservaSolicitacaoTecnica.class));
    }

    @Test
    void repeticaoComMesmoPayloadReutilizaReservaSemNovaEscrita() {
        Fixture f = fixture();
        UUID idAgente = UUID.randomUUID();
        RegistrarReservaTecnicaRequest request = request(f, "12345678901234567890", idAgente, null);
        AtomicReference<ReservaSolicitacaoTecnica> persistida = new AtomicReference<>();
        when(solicitacaoRepository.findById(f.filho().getId())).thenReturn(Optional.of(f.filho()));
        when(referenciaValidator.validarAgente(idAgente)).thenReturn(agente(idAgente));
        when(reservaRepository.findBySolicitacaoId(f.filho().getId()))
                .thenAnswer(ignorado -> Optional.ofNullable(persistida.get()));
        when(reservaRepository.save(any())).thenAnswer(invocation -> {
            ReservaSolicitacaoTecnica reserva = invocation.getArgument(0);
            ReflectionTestUtils.setField(reserva, "id", UUID.randomUUID());
            persistida.set(reserva);
            return reserva;
        });

        var primeira = useCases.registrarOuAlterar(f.filho().getId(), request, UUID.randomUUID());
        var repetida = useCases.registrarOuAlterar(f.filho().getId(), request, UUID.randomUUID());

        assertThat(repetida.id()).isEqualTo(primeira.id());
        verify(reservaRepository).save(any(ReservaSolicitacaoTecnica.class));
    }

    @Test
    void rejeitaFormatoNaoNumericoAntesDePersistir() {
        Fixture f = fixture();
        RegistrarReservaTecnicaRequest request = request(f, "12A34", UUID.randomUUID(), null);
        when(solicitacaoRepository.findById(f.filho().getId())).thenReturn(Optional.of(f.filho()));

        assertThatThrownBy(() -> useCases.registrarOuAlterar(
                f.filho().getId(), request, UUID.randomUUID()))
                .isInstanceOfSatisfying(PreenchimentoInvalidoException.class, ex ->
                        assertThat(ex.getErros()).containsKey("numero"));

        verify(reservaRepository, never()).save(any());
        verify(referenciaValidator, never()).validarAgente(any());
    }

    @Test
    void rejeitaVersaoObsoletaComVersaoAtualParaRecarga() {
        Fixture f = fixture();
        RegistrarReservaTecnicaRequest request = new RegistrarReservaTecnicaRequest(
                f.filho().getVersao() - 1, "123", LocalDate.of(2026, 8, 1), UUID.randomUUID(), null);
        when(solicitacaoRepository.findById(f.filho().getId())).thenReturn(Optional.of(f.filho()));

        assertThatThrownBy(() -> useCases.registrarOuAlterar(
                f.filho().getId(), request, UUID.randomUUID()))
                .isInstanceOfSatisfying(ConflitoVersaoException.class, ex -> {
                    assertThat(ex.getVersaoEsperada()).isEqualTo(request.versaoEsperada());
                    assertThat(ex.getVersaoAtual()).isEqualTo(f.filho().getVersao());
                });

        verify(reservaRepository, never()).save(any());
    }

    @Test
    void propagaErroDeAgenteInativoSemPersistir() {
        Fixture f = fixture();
        UUID idAgente = UUID.randomUUID();
        RegistrarReservaTecnicaRequest request = request(f, "900", idAgente, null);
        when(solicitacaoRepository.findById(f.filho().getId())).thenReturn(Optional.of(f.filho()));
        when(referenciaValidator.validarAgente(idAgente)).thenThrow(
                new PreenchimentoInvalidoException(java.util.Map.of(
                        "idAgenteResponsavel", "Agente inativo")));

        assertThatThrownBy(() -> useCases.registrarOuAlterar(
                f.filho().getId(), request, UUID.randomUUID()))
                .isInstanceOf(PreenchimentoInvalidoException.class);

        verify(reservaRepository, never()).save(any());
    }

    @Test
    void bloqueiaAlteracaoDeReservaDepoisDeFatoOperacional() {
        Fixture f = fixture();
        UUID idAgente = UUID.randomUUID();
        ReservaSolicitacaoTecnica reserva = new ReservaSolicitacaoTecnica(
                f.filho(), new java.math.BigInteger("100"), LocalDate.of(2026, 8, 1), idAgente,
                "Agente inicial", null, UUID.randomUUID());
        ReflectionTestUtils.setField(reserva, "id", UUID.randomUUID());
        f.filho().getItens().getFirst().registrarEntregaTecnica(java.math.BigDecimal.ONE);
        RegistrarReservaTecnicaRequest request = request(f, "200", idAgente, "Correção da reserva externa");
        when(solicitacaoRepository.findById(f.filho().getId())).thenReturn(Optional.of(f.filho()));
        when(referenciaValidator.validarAgente(idAgente)).thenReturn(agente(idAgente));
        when(reservaRepository.findBySolicitacaoId(f.filho().getId())).thenReturn(Optional.of(reserva));

        assertThatThrownBy(() -> useCases.registrarOuAlterar(
                f.filho().getId(), request, UUID.randomUUID()))
                .isInstanceOf(com.nexo.app.shared.domain.exception.InvarianteVioladaException.class)
                .hasMessageContaining("primeiro fato operacional");

        assertThat(reserva.getNumeroApi()).isEqualTo("100");
        verify(reservaRepository, never()).save(any());
        verify(gravadorEvento, never()).registrarFilho(any(), any(), any(), any());
    }

    @Test
    void alteraReservaAntesDoFatoComJustificativaERegistraValoresAnteriorENovo() {
        Fixture f = fixture();
        UUID idAgenteInicial = UUID.randomUUID();
        ReservaSolicitacaoTecnica reserva = new ReservaSolicitacaoTecnica(
                f.filho(), new java.math.BigInteger("100"), LocalDate.of(2026, 8, 1), idAgenteInicial,
                "Agente inicial", null, UUID.randomUUID());
        ReflectionTestUtils.setField(reserva, "id", UUID.randomUUID());
        UUID idAgenteNovo = UUID.randomUUID();
        RegistrarReservaTecnicaRequest request = request(
                f, "200", idAgenteNovo, "Número externo corrigido");
        when(solicitacaoRepository.findById(f.filho().getId())).thenReturn(Optional.of(f.filho()));
        when(referenciaValidator.validarAgente(idAgenteNovo)).thenReturn(agente(idAgenteNovo));
        when(reservaRepository.findBySolicitacaoId(f.filho().getId())).thenReturn(Optional.of(reserva));
        when(reservaRepository.save(reserva)).thenReturn(reserva);

        useCases.registrarOuAlterar(f.filho().getId(), request, UUID.randomUUID());

        assertThat(reserva.getNumeroApi()).isEqualTo("200");
        assertThat(reserva.getIdUsuarioAtualizacao()).isNotNull();
        assertThat(reserva.getAtualizadaEm()).isNotNull();
        ArgumentCaptor<PayloadEventoSolicitacao> payload = ArgumentCaptor.forClass(PayloadEventoSolicitacao.class);
        verify(gravadorEvento).registrarFilho(
                org.mockito.ArgumentMatchers.same(f.filho()),
                org.mockito.ArgumentMatchers.eq(TipoEventoSolicitacao.RESERVA_ALTERADA),
                payload.capture(), any());
        assertThat(payload.getValue().alteracoes()).extracting(alteracao -> alteracao.caminho())
                .containsExactlyInAnyOrder("reserva.numero", "reserva.idAgenteResponsavel");
        assertThat(payload.getValue().alteracoes()).extracting(alteracao -> alteracao.caminho())
                .doesNotContain("reserva.dataCriacao");
        verify(reservaRepository, times(1)).save(reserva);
    }

    private void prepararCriacao(
            Fixture f,
            RegistrarReservaTecnicaRequest request,
            AgenteResponsavelPublico agente
    ) {
        when(solicitacaoRepository.findById(f.filho().getId())).thenReturn(Optional.of(f.filho()));
        when(referenciaValidator.validarAgente(request.idAgenteResponsavel())).thenReturn(agente);
        when(reservaRepository.findBySolicitacaoId(f.filho().getId())).thenReturn(Optional.empty());
        when(reservaRepository.save(any())).thenAnswer(invocation -> {
            ReservaSolicitacaoTecnica reserva = invocation.getArgument(0);
            ReflectionTestUtils.setField(reserva, "id", UUID.randomUUID());
            return reserva;
        });
    }

    private static RegistrarReservaTecnicaRequest request(
            Fixture f,
            String numero,
            UUID idAgente,
            String justificativa
    ) {
        return new RegistrarReservaTecnicaRequest(
                f.filho().getVersao(), numero, LocalDate.of(2026, 8, 1), idAgente, justificativa);
    }

    private static AgenteResponsavelPublico agente(UUID id) {
        return new AgenteResponsavelPublico(
                id, "Agente da reserva", "agente@example.com", null, "CONCESSIONARIA");
    }

    private static Fixture fixture() {
        ObraPublica obra = new ObraPublica(
                UUID.randomUUID(), "001", "Obra", TipoObraPublico.OBRA, true,
                UUID.randomUUID(), "OBRAS", true, UUID.randomUUID(), Set.of(), null, List.of());
        TipoEstoque fisico = comId(new TipoEstoque("FISICO"));
        Estoque almoxarifado = comId(new Estoque("ALMOXARIFADO", fisico));
        almoxarifado.vincularBaseOperacional(obra.idBaseOperacional());
        TipoEstoque tipoObra = comId(new TipoEstoque("OBRA"));
        Estoque estoqueObra = comId(new Estoque("OBRA 001", tipoObra));
        estoqueObra.vincularBaseOperacional(obra.idBaseOperacional());
        estoqueObra.vincularObra(obra.id());
        SolicitacaoMaterial filho = comId(SolicitacaoMaterial.criarTecnica(
                obra, null, TipoSolicitacaoTecnica.ADICAO_COMUM, estoqueObra, almoxarifado,
                null, null, null, UUID.randomUUID()));
        Material material = comId(new Material(
                "MAT-RESERVA", "Material de reserva", "UN", "MATR", null, null,
                false, UUID.randomUUID()));
        filho.adicionarItemTecnico(material, BigDecimal.ONE);
        ReflectionTestUtils.setField(filho, "versao", 2L);
        return new Fixture(filho);
    }

    private static <T extends BaseEntity> T comId(T entidade) {
        ReflectionTestUtils.setField(entidade, "id", UUID.randomUUID());
        return entidade;
    }

    private record Fixture(SolicitacaoMaterial filho) {
    }
}
