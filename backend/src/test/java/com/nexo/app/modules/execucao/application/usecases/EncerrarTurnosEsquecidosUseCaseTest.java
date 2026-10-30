package com.nexo.app.modules.execucao.application.usecases;

import com.nexo.app.modules.execucao.application.dto.UltimoLancamentoDoTurno;
import com.nexo.app.modules.execucao.domain.StatusTurno;
import com.nexo.app.modules.execucao.domain.Turno;
import com.nexo.app.modules.execucao.infrastructure.persistence.ExecucaoComercialRepository;
import com.nexo.app.modules.execucao.infrastructure.persistence.TurnoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EncerrarTurnosEsquecidosUseCaseTest {

    private final CorteDiarioDoTurno corte = new CorteDiarioDoTurno("22:00", "America/Sao_Paulo");

    @Nested
    @DisplayName("Horário de corte")
    class Corte {

        @Test
        @DisplayName("antes das 22h o último corte é o de ontem; depois, o de hoje")
        void ultimoCorte() {
            assertThat(corte.ultimoCorteAte(OffsetDateTime.parse("2026-10-13T21:59:00-03:00")))
                    .isEqualTo(OffsetDateTime.parse("2026-10-12T22:00:00-03:00"));
            assertThat(corte.ultimoCorteAte(OffsetDateTime.parse("2026-10-13T22:00:00-03:00")))
                    .isEqualTo(OffsetDateTime.parse("2026-10-13T22:00:00-03:00"));
            // Instante em UTC (servidor): 01h UTC do dia 14 ainda é 22h do dia 13 no Brasil.
            assertThat(corte.ultimoCorteAte(OffsetDateTime.parse("2026-10-14T01:30:00Z")))
                    .isEqualTo(OffsetDateTime.parse("2026-10-13T22:00:00-03:00"));
        }

        @Test
        @DisplayName("turno aberto depois do corte vale até o corte do dia seguinte")
        void corteDoTurno() {
            assertThat(corte.corteDoTurno(OffsetDateTime.parse("2026-10-13T08:00:00-03:00")))
                    .isEqualTo(OffsetDateTime.parse("2026-10-13T22:00:00-03:00"));
            assertThat(corte.corteDoTurno(OffsetDateTime.parse("2026-10-13T22:30:00-03:00")))
                    .isEqualTo(OffsetDateTime.parse("2026-10-14T22:00:00-03:00"));
        }
    }

    @Nested
    @DisplayName("Encerramento")
    class Encerramento {

        private final TurnoRepository turnoRepository = mock(TurnoRepository.class);
        private final ExecucaoComercialRepository execucaoRepository = mock(ExecucaoComercialRepository.class);
        private final EncerrarTurnosEsquecidosUseCase useCase =
                new EncerrarTurnosEsquecidosUseCase(turnoRepository, execucaoRepository, corte);

        private final OffsetDateTime agora = OffsetDateTime.parse("2026-10-13T22:10:00-03:00");

        @Test
        @DisplayName("fim do turno é o último lançamento, não a hora do encerramento")
        void fimNoUltimoLancamento() {
            Turno turno = turnoAbertoEm("2026-10-13T08:09:00-03:00");
            OffsetDateTime ultimo = OffsetDateTime.parse("2026-10-13T16:40:00-03:00");
            when(turnoRepository.findByStatusAndAtivoTrueAndDataInicioBefore(StatusTurno.ABERTO,
                    OffsetDateTime.parse("2026-10-13T22:00:00-03:00"))).thenReturn(List.of(turno));
            when(execucaoRepository.buscarUltimosLancamentos(anyCollection()))
                    .thenReturn(List.of(new UltimoLancamentoDoTurno(turno.getId(), ultimo)));

            int encerrados = useCase.executar(agora);

            assertThat(encerrados).isEqualTo(1);
            assertThat(turno.getStatus()).isEqualTo(StatusTurno.FECHADO);
            assertThat(turno.getDataFim()).isEqualTo(ultimo);
            assertThat(turno.isEncerradoAutomaticamente()).isTrue();
            assertThat(turno.getMotivoEncerramento())
                    .contains("corte das 22:00")
                    .contains("último lançamento (13/10 16:40)");
            verify(turnoRepository).saveAll(List.of(turno));
        }

        @Test
        @DisplayName("sem lançamentos, o fim é a própria abertura")
        void semLancamentos() {
            Turno turno = turnoAbertoEm("2026-10-13T14:03:00-03:00");
            when(turnoRepository.findByStatusAndAtivoTrueAndDataInicioBefore(eq(StatusTurno.ABERTO), any()))
                    .thenReturn(List.of(turno));
            when(execucaoRepository.buscarUltimosLancamentos(anyCollection())).thenReturn(List.of());

            useCase.executar(agora);

            assertThat(turno.getDataFim()).isEqualTo(turno.getDataInicio());
            assertThat(turno.getMotivoEncerramento()).contains("sem lançamentos");
        }

        @Test
        @DisplayName("relógio adiantado do aparelho não estende o turno além de agora")
        void lancamentoNoFuturo() {
            Turno turno = turnoAbertoEm("2026-10-13T08:00:00-03:00");
            when(turnoRepository.findByStatusAndAtivoTrueAndDataInicioBefore(eq(StatusTurno.ABERTO), any()))
                    .thenReturn(List.of(turno));
            when(execucaoRepository.buscarUltimosLancamentos(anyCollection())).thenReturn(List.of(
                    new UltimoLancamentoDoTurno(turno.getId(), OffsetDateTime.parse("2026-10-15T10:00:00-03:00"))));

            useCase.executar(agora);

            assertThat(turno.getDataFim()).isEqualTo(agora);
        }

        @Test
        @DisplayName("nada a encerrar não grava nada")
        void nadaAEncerrar() {
            when(turnoRepository.findByStatusAndAtivoTrueAndDataInicioBefore(eq(StatusTurno.ABERTO), any()))
                    .thenReturn(List.of());

            assertThat(useCase.executar(agora)).isZero();
            verify(execucaoRepository, never()).buscarUltimosLancamentos(anyCollection());
            verify(turnoRepository, never()).saveAll(any());
        }
    }

    @Nested
    @DisplayName("Lançamento tardio")
    class LancamentoTardio {

        @Test
        @DisplayName("lançamento que chega depois do encerramento automático estende o fim")
        void estendeFim() {
            Turno turno = turnoAbertoEm("2026-10-13T08:00:00-03:00");
            turno.encerrarAutomaticamente(OffsetDateTime.parse("2026-10-13T16:00:00-03:00"), "motivo");

            turno.registrarLancamentoTardio(OffsetDateTime.parse("2026-10-13T17:30:00-03:00"));
            assertThat(turno.getDataFim()).isEqualTo(OffsetDateTime.parse("2026-10-13T17:30:00-03:00"));

            turno.registrarLancamentoTardio(OffsetDateTime.parse("2026-10-13T12:00:00-03:00"));
            assertThat(turno.getDataFim()).isEqualTo(OffsetDateTime.parse("2026-10-13T17:30:00-03:00"));
        }

        @Test
        @DisplayName("turno fechado pela equipe não muda com lançamento tardio")
        void fechadoPelaEquipe() {
            Turno turno = turnoAbertoEm("2026-10-13T08:00:00-03:00");
            turno.fechar();
            OffsetDateTime fim = turno.getDataFim();

            turno.registrarLancamentoTardio(fim.plusHours(2));

            assertThat(turno.getDataFim()).isEqualTo(fim);
            assertThat(turno.isEncerradoAutomaticamente()).isFalse();
        }
    }

    private static Turno turnoAbertoEm(String abertura) {
        Turno turno = new Turno(UUID.randomUUID(), UUID.randomUUID());
        ReflectionTestUtils.setField(turno, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(turno, "dataInicio", OffsetDateTime.parse(abertura));
        return turno;
    }
}
