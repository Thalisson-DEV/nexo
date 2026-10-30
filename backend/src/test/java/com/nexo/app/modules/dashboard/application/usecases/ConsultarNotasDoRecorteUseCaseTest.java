package com.nexo.app.modules.dashboard.application.usecases;

import com.nexo.app.modules.dashboard.application.dto.NotaDoRecorteResponse;
import com.nexo.app.modules.dashboard.application.dto.NotasDoRecorteFilter;
import com.nexo.app.modules.dashboard.domain.CalendarioOperacional;
import com.nexo.app.modules.execucao.ExecucaoComercialApi;
import com.nexo.app.modules.execucao.application.dto.ExecucaoComercialResponse;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static com.nexo.app.modules.dashboard.application.usecases.GerarDashboardOperacionalUseCaseTest.equipe;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsultarNotasDoRecorteUseCaseTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-10-09T15:00:00-03:00");

    private final EquipesDoRecorte equipesDoRecorte = mock(EquipesDoRecorte.class);
    private final ExecucaoComercialApi execucaoApi = mock(ExecucaoComercialApi.class);
    private ConsultarNotasDoRecorteUseCase useCase;

    private final UUID idEq01 = UUID.randomUUID();
    private final UUID idEq02 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new ConsultarNotasDoRecorteUseCase(equipesDoRecorte, execucaoApi,
                Clock.fixed(AGORA.toInstant(), CalendarioOperacional.FUSO));
        EquipeResponse eq01 = equipe(idEq01, "EQ-01", new BigDecimal("22000"));
        EquipeResponse eq02 = equipe(idEq02, "LV-02", null);
        when(equipesDoRecorte.listar(any(), any())).thenReturn(List.of(eq01, eq02));
        when(execucaoApi.listarConcluidasDasEquipes(any(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(new PageImpl<>(List.of(nota(eq01))));
    }

    @Test
    @DisplayName("Sem período usa o dia de hoje em São Paulo e todas as equipes do recorte")
    @SuppressWarnings("unchecked")
    void usaHojeEEquipesDoRecorte() {
        PageResponse<NotaDoRecorteResponse> pagina = useCase.executar(
                new NotasDoRecorteFilter(null, null, null, null, null, null, null), PageRequest.of(0, 50));

        ArgumentCaptor<Collection<UUID>> equipes = ArgumentCaptor.forClass(Collection.class);
        verify(execucaoApi).listarConcluidasDasEquipes(equipes.capture(),
                eq(OffsetDateTime.parse("2026-10-09T00:00:00-03:00")),
                eq(OffsetDateTime.parse("2026-10-10T00:00:00-03:00")),
                eq(null), eq(false), any());
        assertThat(equipes.getValue()).containsExactlyInAnyOrder(idEq01, idEq02);

        NotaDoRecorteResponse linha = pagina.getContent().getFirst();
        assertThat(linha.codigoEquipe()).isEqualTo("EQ-01");
        assertThat(linha.baseOperacional()).isEqualTo("Base Salvador");
        assertThat(linha.valor()).isEqualByComparingTo("350.00");
    }

    @Test
    @DisplayName("Filtro de equipe restringe às equipes do recorte; equipe fora do recorte não traz notas")
    @SuppressWarnings("unchecked")
    void restringeEquipe() {
        UUID foraDoRecorte = UUID.randomUUID();
        useCase.executar(new NotasDoRecorteFilter(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-09"),
                null, null, foraDoRecorte, null, true), PageRequest.of(0, 50));

        ArgumentCaptor<Collection<UUID>> equipes = ArgumentCaptor.forClass(Collection.class);
        verify(execucaoApi).listarConcluidasDasEquipes(equipes.capture(), any(), any(), eq(null), eq(true), any());
        assertThat(equipes.getValue()).isEmpty();
    }

    @Test
    @DisplayName("Ordenação só por colunas do banco e página limitada a 500")
    void paginacaoSegura() {
        Pageable segura = ConsultarNotasDoRecorteUseCase.paginacaoSegura(
                PageRequest.of(2, 5000, Sort.by("codigoEquipe")));
        assertThat(segura.getPageSize()).isEqualTo(ConsultarNotasDoRecorteUseCase.TAMANHO_MAXIMO);
        assertThat(segura.getPageNumber()).isEqualTo(2);
        assertThat(segura.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "dataLancamento"));

        Pageable porValor = ConsultarNotasDoRecorteUseCase.paginacaoSegura(
                PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "valorTotalFaturado")));
        assertThat(porValor.getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "valorTotalFaturado"));
    }

    @Test
    @DisplayName("Período invertido é recusado")
    void periodoInvertido() {
        assertThatThrownBy(() -> useCase.executar(new NotasDoRecorteFilter(LocalDate.parse("2026-10-09"),
                LocalDate.parse("2026-10-01"), null, null, null, null, null), PageRequest.of(0, 50)))
                .isInstanceOf(InvarianteVioladaException.class);
    }

    private static ExecucaoComercialResponse nota(EquipeResponse equipe) {
        return new ExecucaoComercialResponse(UUID.randomUUID(), "NOTA-1", UUID.randomUUID(), null, equipe, null,
                null, null, AGORA.minusHours(2), null, new BigDecimal("350.00"), null, null, List.of(), List.of(), null, null, null, 0);
    }
}
