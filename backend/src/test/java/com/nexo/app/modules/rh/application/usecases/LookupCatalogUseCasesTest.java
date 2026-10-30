package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.infrastructure.persistence.BaseOperacionalLookupProjection;
import com.nexo.app.modules.rh.infrastructure.persistence.BaseOperacionalRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.CentroFaturamentoRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.ProcessoNegocioLookupProjection;
import com.nexo.app.modules.rh.infrastructure.persistence.ProcessoNegocioRepository;
import com.nexo.app.modules.rh.application.mappers.BaseOperacionalMapper;
import com.nexo.app.modules.rh.application.mappers.ProcessoNegocioMapper;
import com.nexo.app.shared.application.dto.LookupOptionDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LookupCatalogUseCasesTest {

    @Test
    void basesListamSomenteProjectionMinimaOrdenadaPeloRepositorio() {
        BaseOperacionalRepository repository = mock(BaseOperacionalRepository.class);
        BaseOperacionalLookupProjection row = mock(BaseOperacionalLookupProjection.class);
        UUID id = UUID.randomUUID();
        when(row.getId()).thenReturn(id);
        when(row.getLabel()).thenReturn("Base Norte");
        when(repository.listarOpcoesAtivas()).thenReturn(List.of(row));
        BaseOperacionalUseCases useCase = new BaseOperacionalUseCases(
                repository, mock(CentroFaturamentoRepository.class), mock(BaseOperacionalMapper.class));

        assertThat(useCase.listarOpcoesAtivas())
                .containsExactly(new LookupOptionDto(id.toString(), "Base Norte"));
        verify(repository).listarOpcoesAtivas();
    }

    @Test
    void processosListamSomenteProjectionMinima() {
        ProcessoNegocioRepository repository = mock(ProcessoNegocioRepository.class);
        ProcessoNegocioLookupProjection row = mock(ProcessoNegocioLookupProjection.class);
        UUID id = UUID.randomUUID();
        when(row.getId()).thenReturn(id);
        when(row.getLabel()).thenReturn("Comercial");
        when(repository.listarOpcoesAtivas()).thenReturn(List.of(row));
        ProcessoNegocioUseCases useCase = new ProcessoNegocioUseCases(
                repository, mock(ProcessoNegocioMapper.class));

        assertThat(useCase.listarOpcoesAtivas())
                .containsExactly(new LookupOptionDto(id.toString(), "Comercial"));
        verify(repository).listarOpcoesAtivas();
    }
}
