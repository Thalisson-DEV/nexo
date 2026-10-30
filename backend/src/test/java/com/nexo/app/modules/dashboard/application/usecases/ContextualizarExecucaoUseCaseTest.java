package com.nexo.app.modules.dashboard.application.usecases;

import com.nexo.app.modules.contrato.AcaoResponse;
import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.execucao.ExecucaoComercialApi;
import com.nexo.app.modules.execucao.TurnoApi;
import com.nexo.app.modules.execucao.application.dto.ExecucaoAcaoResponse;
import com.nexo.app.modules.execucao.application.dto.ExecucaoComercialResponse;
import com.nexo.app.modules.execucao.application.dto.LancamentoResumido;
import com.nexo.app.modules.execucao.domain.StatusExecucao;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.EquipeResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.nexo.app.modules.dashboard.application.usecases.ConsultarTurnosUseCaseTest.turno;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContextualizarExecucaoUseCaseTest {

    private final ExecucaoComercialApi execucaoApi = mock(ExecucaoComercialApi.class);
    private final TurnoApi turnoApi = mock(TurnoApi.class);
    private final EquipeApi equipeApi = mock(EquipeApi.class);
    private final ContratoApi contratoApi = mock(ContratoApi.class);
    private final ContextualizarExecucaoUseCase useCase =
            new ContextualizarExecucaoUseCase(execucaoApi, turnoApi, equipeApi, contratoApi,
                    MetasMensais.porEquipe(id -> equipeApi.buscarPorIdResponse(id)));

    @Test
    @DisplayName("Contexto: dia da equipe contra a meta, média dos 30 dias anteriores e valor só das ações da nota")
    void montaContexto() {
        UUID idEquipe = UUID.randomUUID();
        UUID idContrato = UUID.randomUUID();
        var turno = turno(idEquipe, "2026-10-09T07:00:00-03:00", null, 0);
        var equipe = new EquipeResponse(idEquipe, "EQ-01", null, null, null, null, null, null, idContrato, true,
                List.of(), new BigDecimal("21000"));
        var acaoDaNota = new AcaoResponse(UUID.randomUUID(), "A1", "Religação", true, null, null);
        OffsetDateTime lancamento = OffsetDateTime.parse("2026-10-09T10:00:00-03:00");
        var execucao = new ExecucaoComercialResponse(UUID.randomUUID(), "N-1", turno.id(), null, equipe, null, null, null,
                lancamento, StatusExecucao.CONCLUIDO, new BigDecimal("150"), null, null,
                List.of(new ExecucaoAcaoResponse(UUID.randomUUID(), acaoDaNota)), List.of(), null, null, null, 0);

        when(execucaoApi.buscarPorId(execucao.id())).thenReturn(execucao);
        when(equipeApi.buscarPorIdResponse(idEquipe)).thenReturn(Optional.of(equipe));
        when(turnoApi.buscarPorId(turno.id())).thenReturn(turno);
        when(execucaoApi.listarLancamentosDoTurno(turno.id())).thenReturn(List.of());
        when(execucaoApi.listarLancamentosResumidos(
                eq(OffsetDateTime.parse("2026-10-09T00:00:00-03:00")), any(), any()))
                .thenReturn(List.of(resumido(idEquipe, "150"), resumido(idEquipe, "350")));
        when(execucaoApi.listarLancamentosResumidos(
                eq(OffsetDateTime.parse("2026-09-09T00:00:00-03:00")), any(), any()))
                .thenReturn(List.of(resumido(idEquipe, "100"), resumido(idEquipe, "300")));
        when(contratoApi.listarAcoesComValor(idContrato, LocalDate.of(2026, 10, 9))).thenReturn(List.of(
                new ContratoApi.AcaoValorItem(acaoDaNota.id(), "A1", "Religação", new BigDecimal("150")),
                new ContratoApi.AcaoValorItem(UUID.randomUUID(), "A2", "Outra", new BigDecimal("80"))));

        var contexto = useCase.executar(execucao.id());

        assertThat(contexto.turno().id()).isEqualTo(turno.id());
        assertThat(contexto.diaDaEquipe().notas()).isEqualTo(2);
        assertThat(contexto.diaDaEquipe().producao()).isEqualByComparingTo("500");
        assertThat(contexto.diaDaEquipe().percentualMetaDiaria()).isEqualByComparingTo("50.0");
        assertThat(contexto.referenciaEquipe().notas()).isEqualTo(2);
        assertThat(contexto.referenciaEquipe().valorMedioNota()).isEqualByComparingTo("200");
        assertThat(contexto.valoresPorAcao()).singleElement()
                .satisfies(v -> assertThat(v.idAcao()).isEqualTo(acaoDaNota.id()));
    }

    private static LancamentoResumido resumido(UUID idEquipe, String valor) {
        return new LancamentoResumido(UUID.randomUUID(), idEquipe, null, null, OffsetDateTime.now(), new BigDecimal(valor));
    }
}
