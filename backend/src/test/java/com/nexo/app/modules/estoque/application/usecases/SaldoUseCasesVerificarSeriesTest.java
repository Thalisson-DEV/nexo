package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.SaldoSerieResponse;
import com.nexo.app.modules.estoque.application.mappers.SaldoMapper;
import com.nexo.app.modules.estoque.application.mappers.SaldoSerieMapper;
import com.nexo.app.modules.estoque.domain.SaldoSerie;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoSerieRepository;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SaldoUseCasesVerificarSeriesTest {

    private final SaldoSerieRepository series = mock(SaldoSerieRepository.class);
    private final SaldoSerieMapper mapper = mock(SaldoSerieMapper.class);
    private final SaldoUseCases useCases = new SaldoUseCases(mock(SaldoRepository.class), mock(SaldoMapper.class), series, mapper);

    @Test
    void consultaUmaVezSemVaziosNemRepetidasEDevolveAsAtivas() {
        SaldoSerie existente = mock(SaldoSerie.class);
        SaldoSerieResponse resposta = new SaldoSerieResponse(null, null, null, "ABC1", null, true, null, null);
        when(series.findAllByNumSerieInAndAtivoTrue(List.of("ABC1", "ABC2"))).thenReturn(List.of(existente));
        when(mapper.toResponse(existente)).thenReturn(resposta);

        List<SaldoSerieResponse> r = useCases.verificarSeries(Arrays.asList(" ABC1 ", "ABC2", "ABC1", "", null));

        assertThat(r).containsExactly(resposta);
    }

    @Test
    void listaVaziaNaoVaiAoBanco() {
        assertThat(useCases.verificarSeries(List.of("  "))).isEmpty();
        verify(series, never()).findAllByNumSerieInAndAtivoTrue(any());
    }
}
