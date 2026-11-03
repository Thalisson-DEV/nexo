package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.execucao.domain.StatusTurno;
import com.nexo.app.modules.obras.application.dto.TurnoObraFilter;
import com.nexo.app.modules.obras.application.dto.TurnoObraResponses;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConsultarTurnosObraTest {

    private static TurnoObraResponses.Linha linha(StatusTurno status, String realizado, String meta, boolean semRelatorio, int ocorrencias) {
        BigDecimal valor = new BigDecimal(realizado);
        BigDecimal valorMeta = meta != null ? new BigDecimal(meta) : null;
        return new TurnoObraResponses.Linha(UUID.randomUUID(), UUID.randomUUID(), "FIC-01", "PESADA", "BASE", "ABC1D23", status,
                OffsetDateTime.now(), null, 60, false, false, null, "Supervisor", 5, 0, valor, valorMeta, null,
                ConsultarTurnosObra.bateu(valor, valorMeta), semRelatorio, ocorrencias, 0, 0, 2, List.of("7000001"));
    }

    @Test
    void realizadoIgualAMetaContaComoBateu() {
        assertThat(ConsultarTurnosObra.bateu(new BigDecimal("100.00"), new BigDecimal("100"))).isTrue();
        assertThat(ConsultarTurnosObra.bateu(new BigDecimal("99.99"), new BigDecimal("100"))).isFalse();
        assertThat(ConsultarTurnosObra.bateu(new BigDecimal("100.00"), null)).isFalse();
    }

    @Test
    void turnoSemMetaNaoEntraEmBateuNemEmNaoBateu() {
        TurnoObraResponses.Linha semMeta = linha(StatusTurno.FECHADO, "10", null, false, 0);
        TurnoObraResponses.Linha bateu = linha(StatusTurno.FECHADO, "200", "100", false, 0);
        TurnoObraResponses.Linha naoBateu = linha(StatusTurno.FECHADO, "50", "100", false, 0);

        assertThat(ConsultarTurnosObra.atendeMeta(semMeta, TurnoObraFilter.Meta.BATEU)).isFalse();
        assertThat(ConsultarTurnosObra.atendeMeta(semMeta, TurnoObraFilter.Meta.NAO_BATEU)).isFalse();
        assertThat(ConsultarTurnosObra.atendeMeta(bateu, TurnoObraFilter.Meta.BATEU)).isTrue();
        assertThat(ConsultarTurnosObra.atendeMeta(naoBateu, TurnoObraFilter.Meta.NAO_BATEU)).isTrue();
        assertThat(ConsultarTurnosObra.atendeMeta(semMeta, null)).isTrue();
    }

    @Test
    void indicadoresResumemORecorte() {
        TurnoObraResponses.Indicadores indicadores = ConsultarTurnosObra.indicadores(List.of(
                linha(StatusTurno.ABERTO, "200", "100", false, 0),
                linha(StatusTurno.FECHADO, "50", "100", true, 2),
                linha(StatusTurno.FECHADO, "0", null, true, 0)));

        assertThat(indicadores.turnos()).isEqualTo(3);
        assertThat(indicadores.emAndamento()).isEqualTo(1);
        assertThat(indicadores.minutosTotais()).isEqualTo(180);
        assertThat(indicadores.comMeta()).isEqualTo(2);
        assertThat(indicadores.bateuMeta()).isEqualTo(1);
        assertThat(indicadores.semRelatorio()).isEqualTo(2);
        assertThat(indicadores.comOcorrencias()).isEqualTo(1);
        assertThat(indicadores.servicosExecutados()).isEqualTo(6);
        assertThat(indicadores.valorExecutado()).isEqualByComparingTo("250");
    }
}
