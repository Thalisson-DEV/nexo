package com.nexo.app.modules.execucao.application.usecases;

import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.estoque.EstoqueApi;
import com.nexo.app.modules.execucao.application.dto.AlterarExecucaoRequest;
import com.nexo.app.modules.execucao.application.dto.ExecucaoComercialResponse;
import com.nexo.app.modules.execucao.application.dto.RegistrarExecucaoRequest;
import com.nexo.app.modules.execucao.application.mappers.ExecucaoComercialMapper;
import com.nexo.app.modules.execucao.domain.DetalhesEventoNota;
import com.nexo.app.modules.execucao.domain.ExecucaoComercial;
import com.nexo.app.modules.execucao.domain.OrigemAlteracaoNota;
import com.nexo.app.modules.execucao.domain.OrigemMaterial;
import com.nexo.app.modules.execucao.domain.TipoNota;
import com.nexo.app.modules.execucao.infrastructure.persistence.ExecucaoComercialRepository;
import com.nexo.app.modules.execucao.infrastructure.persistence.TurnoRepository;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.MunicipioApi;
import com.nexo.app.modules.rh.MunicipioResponse;
import com.nexo.app.shared.application.dto.OperacaoMaterial;
import com.nexo.app.shared.domain.events.ExecucaoAlteradaEvent;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlteracaoNotaUseCaseTest {

    @Mock private ExecucaoComercialRepository repository;
    @Mock private TurnoRepository turnoRepository;
    @Mock private ExecucaoComercialMapper mapper;
    @Mock private EquipeApi equipeApi;
    @Mock private MunicipioApi municipioApi;
    @Mock private ContratoApi contratoApi;
    @Mock private EstoqueApi estoqueApi;
    @Mock private TipoServicoUseCases tipoServicoUseCases;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private CorteDiarioDoTurno corteDiario;
    @Mock private HistoricoNotaService historico;

    @InjectMocks private ExecucaoComercialUseCases useCases;

    private final UUID idEquipe = UUID.randomUUID();
    private final UUID usuario = UUID.randomUUID();
    private final UUID cabo = UUID.randomUUID();
    private ExecucaoComercial nota;

    @BeforeEach
    void setUp() {
        nota = notaLancadaHa(2);
        lenient().when(repository.findById(nota.getId())).thenReturn(Optional.of(nota));
        lenient().when(mapper.toResponse(any())).thenReturn(new ExecucaoComercialResponse(nota.getId(), "9293818293", null,
                null, null, null, null, null, nota.getDataLancamento(), null, BigDecimal.ZERO, null, null, List.of(), List.of(),
                null, null, null, 0));
        lenient().when(historico.execucaoDaOperacao(any())).thenReturn(Optional.empty());
    }

    private ExecucaoComercial notaLancadaHa(int dias) {
        var e = new ExecucaoComercial("9293818293", idEquipe, UUID.randomUUID(), null,
                OffsetDateTime.now().minusDays(dias), null, null, null);
        e.adicionarMaterial(cabo, new BigDecimal("10"), OperacaoMaterial.APLICADO, null);
        e.concluir(BigDecimal.ZERO);
        ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
        return e;
    }

    private AlterarExecucaoRequest seteMetros(UUID idOperacao) {
        return new AlterarExecucaoRequest(List.of(),
                List.of(new RegistrarExecucaoRequest.MaterialItem(cabo, new BigDecimal("7"), OperacaoMaterial.APLICADO, null, null)),
                "lancei 10 m, eram 7", idOperacao, null);
    }

    @Test
    @DisplayName("Escritório altera: grava a diferença no histórico e manda o estorno ao estoque")
    void escritorioAlteraEEstornaEstoque() {
        when(equipeApi.buscarIdEquipePorUsuario(usuario)).thenReturn(Optional.empty());

        useCases.alterar(nota.getId(), seteMetros(null), usuario);

        assertThat(nota.getTotalAlteracoes()).isEqualTo(1);
        verify(historico).registrarAlteracao(eq(nota), any(), eq(List.of()), eq("lancei 10 m, eram 7"), any(), eq(usuario),
                eq(OrigemAlteracaoNota.ESCRITORIO), any(), any());
        var evento = ArgumentCaptor.forClass(ExecucaoAlteradaEvent.class);
        verify(eventPublisher).publishEvent(evento.capture());
        assertThat(evento.getValue().estornos()).singleElement()
                .satisfies(i -> assertThat(i.quantidade()).isEqualByComparingTo("3"));
        assertThat(evento.getValue().lancamentos()).isEmpty();
    }

    @Test
    @DisplayName("Campo de outra equipe não altera a nota")
    void campoDeOutraEquipeRecusado() {
        when(equipeApi.buscarIdEquipePorUsuario(usuario)).thenReturn(Optional.of(UUID.randomUUID()));

        assertThatThrownBy(() -> useCases.alterar(nota.getId(), seteMetros(null), usuario))
                .isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Campo não altera nota lançada há mais de 7 dias")
    void campoForaDoPrazoRecusado() {
        var antiga = notaLancadaHa(8);
        when(repository.findById(antiga.getId())).thenReturn(Optional.of(antiga));
        when(equipeApi.buscarIdEquipePorUsuario(usuario)).thenReturn(Optional.of(idEquipe));

        assertThatThrownBy(() -> useCases.alterar(antiga.getId(), seteMetros(null), usuario))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("7 dias");
    }

    @Test
    @DisplayName("Campo altera offline dentro do prazo mesmo se o envio chegar depois")
    void alteracaoOfflineValePelaHoraDoAparelho() {
        var antiga = notaLancadaHa(8);
        when(repository.findById(antiga.getId())).thenReturn(Optional.of(antiga));
        when(equipeApi.buscarIdEquipePorUsuario(usuario)).thenReturn(Optional.of(idEquipe));
        var feitaNoDia6 = new AlterarExecucaoRequest(List.of(), seteMetros(null).materiais(), "eram 7", UUID.randomUUID(),
                OffsetDateTime.now().minusDays(2));

        useCases.alterar(antiga.getId(), feitaNoDia6, usuario);

        assertThat(antiga.getTotalAlteracoes()).isEqualTo(1);
        verify(historico).registrarAlteracao(eq(antiga), any(), any(), any(), any(), any(), eq(OrigemAlteracaoNota.CAMPO), any(), any());
    }

    @Test
    @DisplayName("Reenvio da mesma alteração (fila do aparelho) não altera de novo")
    void reenvioIdempotente() {
        UUID operacao = UUID.randomUUID();
        when(historico.execucaoDaOperacao(operacao)).thenReturn(Optional.of(nota.getId()));

        useCases.alterar(nota.getId(), seteMetros(operacao), usuario);

        assertThat(nota.getTotalAlteracoes()).isZero();
        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(historico, never()).registrarAlteracao(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private AlterarExecucaoRequest dados(String numero, TipoNota tipo, UUID municipio, OffsetDateTime data) {
        return new AlterarExecucaoRequest(List.of(),
                List.of(new RegistrarExecucaoRequest.MaterialItem(cabo, new BigDecimal("10"), OperacaoMaterial.APLICADO, null, null)),
                "corrigindo a nota", null, null, numero, tipo, null, municipio, data);
    }

    @Test
    @DisplayName("Escritório corrige número, município e data: valor recalculado pela nova data e diferença no histórico")
    void escritorioCorrigeDadosDaNota() {
        UUID juazeiro = UUID.randomUUID();
        OffsetDateTime ontem = OffsetDateTime.now().minusDays(1);
        when(equipeApi.buscarIdEquipePorUsuario(usuario)).thenReturn(Optional.empty());
        when(municipioApi.buscarPorIdResponse(juazeiro))
                .thenReturn(Optional.of(new MunicipioResponse(juazeiro, "JUAZEIRO", null, true)));

        useCases.alterar(nota.getId(), dados("9293818299", null, juazeiro, ontem), usuario);

        assertThat(nota.getReferenciaNota()).isEqualTo("9293818299");
        assertThat(nota.getIdMunicipio()).isEqualTo(juazeiro);
        assertThat(nota.getDataLancamento()).isEqualTo(ontem);
        assertThat(nota.getTotalAlteracoes()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DetalhesEventoNota.DadoAlterado>> dadosCaptor = ArgumentCaptor.forClass(List.class);
        verify(historico).registrarAlteracao(eq(nota), any(), dadosCaptor.capture(), any(), any(), any(),
                eq(OrigemAlteracaoNota.ESCRITORIO), any(), any());
        assertThat(dadosCaptor.getValue()).extracting(DetalhesEventoNota.DadoAlterado::rotulo)
                .containsExactly("Número da nota", "Município", "Data do lançamento");
        assertThat(dadosCaptor.getValue().get(1).novo()).isEqualTo("JUAZEIRO");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Campo corrige o número da nota pelo app")
    void campoCorrigeONumero() {
        when(equipeApi.buscarIdEquipePorUsuario(usuario)).thenReturn(Optional.of(idEquipe));

        useCases.alterar(nota.getId(), dados("9293818299", TipoNota.COMUM, null, nota.getDataLancamento()), usuario);

        assertThat(nota.getReferenciaNota()).isEqualTo("9293818299");
        verify(historico).registrarAlteracao(eq(nota), any(), any(), any(), any(), any(), eq(OrigemAlteracaoNota.CAMPO), any(), any());
    }

    @Test
    @DisplayName("Campo não muda município, tipo ou data da nota")
    void campoNaoMudaDadosDoEscritorio() {
        when(equipeApi.buscarIdEquipePorUsuario(usuario)).thenReturn(Optional.of(idEquipe));

        assertThatThrownBy(() -> useCases.alterar(nota.getId(), dados(null, TipoNota.OCORRENCIA, null, null), usuario))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("escritório");
        assertThat(nota.getTipoNota()).isEqualTo(TipoNota.COMUM);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Sem nada diferente, a alteração é recusada")
    void semMudancaRecusada() {
        when(equipeApi.buscarIdEquipePorUsuario(usuario)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCases.alterar(nota.getId(), dados("9293818293", TipoNota.COMUM, null, null), usuario))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("Nada mudou");
    }

    @Test
    @DisplayName("Ocorrência com material da Concessionária não vira nota comum")
    void ocorrenciaComMaterialDaConcessionariaNaoViraComum() {
        var ocorrencia = new ExecucaoComercial("OC123", idEquipe, UUID.randomUUID(), null,
                OffsetDateTime.now().minusDays(1), null, null, null, TipoNota.OCORRENCIA);
        ocorrencia.adicionarMaterial(cabo, BigDecimal.ONE, OperacaoMaterial.APLICADO, null, OrigemMaterial.CONCESSIONARIA);
        ocorrencia.concluir(BigDecimal.ZERO);
        ReflectionTestUtils.setField(ocorrencia, "id", UUID.randomUUID());
        when(repository.findById(ocorrencia.getId())).thenReturn(Optional.of(ocorrencia));
        when(equipeApi.buscarIdEquipePorUsuario(usuario)).thenReturn(Optional.empty());
        var paraComum = new AlterarExecucaoRequest(List.of(),
                List.of(new RegistrarExecucaoRequest.MaterialItem(cabo, BigDecimal.ONE, OperacaoMaterial.APLICADO, null,
                        OrigemMaterial.CONCESSIONARIA)),
                "era nota comum", null, null, null, TipoNota.COMUM, null, null, null);

        assertThatThrownBy(() -> useCases.alterar(ocorrencia.getId(), paraComum, usuario))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("Concessionária");
    }
}
