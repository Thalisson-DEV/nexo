package com.nexo.app.modules.lookup.application.usecases;

import com.nexo.app.modules.estoque.EstoqueLookupApi;
import com.nexo.app.modules.lookup.application.dto.LookupBootstrapDto;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.shared.application.dto.LookupOptionDto;
import com.nexo.app.shared.infrastructure.lookup.LookupMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.List;
import java.util.function.Supplier;

import static com.nexo.app.shared.infrastructure.config.CacheNames.LOOKUP_BOOTSTRAP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LookupBootstrapUseCaseTest {

    @Mock BaseOperacionalApi bases;
    @Mock ProcessoNegocioApi processos;
    @Mock EstoqueLookupApi estoque;
    @Mock CacheManager cacheManager;
    @Mock Cache cache;
    @Mock LookupMetrics metrics;

    private LookupBootstrapUseCase useCase;
    private LookupBootstrapDto expected;

    @BeforeEach
    void setUp() {
        useCase = new LookupBootstrapUseCase(bases, processos, estoque, cacheManager, metrics);
        expected = new LookupBootstrapDto(
                List.of(new LookupOptionDto("base-1", "Base Norte")),
                List.of(new LookupOptionDto("ENTREGA", "Entrega")),
                List.of(new LookupOptionDto("221", "Adição comum")),
                List.of(new LookupOptionDto("PENDENTE", "Pendente")),
                List.of(new LookupOptionDto("processo-1", "Comercial")));
        org.mockito.Mockito.lenient().when(cacheManager.getCache(LOOKUP_BOOTSTRAP)).thenReturn(cache);
        org.mockito.Mockito.lenient().when(bases.listarOpcoesAtivas()).thenReturn(expected.operationalBases());
        org.mockito.Mockito.lenient().when(estoque.listarTiposMovimentacao()).thenReturn(expected.movementTypes());
        org.mockito.Mockito.lenient().when(estoque.listarTiposSolicitacao()).thenReturn(expected.requestTypes());
        org.mockito.Mockito.lenient().when(estoque.listarStatusSolicitacao()).thenReturn(expected.statuses());
        org.mockito.Mockito.lenient().when(processos.listarOpcoesAtivas()).thenReturn(expected.processes());
        // O caso de uso mede a consulta executando o Supplier recebido.
        org.mockito.Mockito.lenient().doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get())
                .when(metrics).observeQuery(anyString(), anyString(), anyString(), any());
    }

    @Test
    void cacheMissCompoeOsCincoCatalogosGravaCacheEContaItens() {
        when(cache.get("bootstrap-v1", LookupBootstrapDto.class)).thenReturn(null);

        LookupBootstrapDto actual = useCase.execute();

        assertThat(actual).isEqualTo(expected);
        verify(cache).put("bootstrap-v1", expected);
        verify(bases).listarOpcoesAtivas();
        verify(processos).listarOpcoesAtivas();
        verify(estoque).listarTiposMovimentacao();
        verify(estoque).listarTiposSolicitacao();
        verify(estoque).listarStatusSolicitacao();
        verify(metrics).recordCacheMiss("bootstrap", "/lookups/bootstrap");
        verify(metrics).recordResultCount("bootstrap", "/lookups/bootstrap", "success", 5);
    }

    @Test
    void cacheHitRetornaCatalogoSemConsultarModulos() {
        when(cache.get("bootstrap-v1", LookupBootstrapDto.class)).thenReturn(expected);

        assertThat(useCase.execute()).isSameAs(expected);

        verify(cache).get("bootstrap-v1", LookupBootstrapDto.class);
        verify(metrics).recordCacheHit("bootstrap", "/lookups/bootstrap");
        verify(bases, never()).listarOpcoesAtivas();
        verify(processos, never()).listarOpcoesAtivas();
        verify(estoque, never()).listarTiposMovimentacao();
    }

    @Test
    void falhaAoLerOuGravarCacheNaoImpedeConsultaDireta() {
        when(cache.get(eq("bootstrap-v1"), eq(LookupBootstrapDto.class)))
                .thenThrow(new IllegalStateException("redis indisponível"));
        doThrow(new IllegalStateException("redis indisponível"))
                .when(cache).put("bootstrap-v1", expected);

        assertThat(useCase.execute()).isEqualTo(expected);

        verify(bases).listarOpcoesAtivas();
        verify(cache).put("bootstrap-v1", expected);
    }

    @Test
    void cacheManagerIndisponivelTambemNaoBloqueiaBootstrap() {
        when(cacheManager.getCache(LOOKUP_BOOTSTRAP))
                .thenThrow(new IllegalStateException("cache manager indisponível"));

        assertThat(useCase.execute()).isEqualTo(expected);

        verify(bases).listarOpcoesAtivas();
        verify(metrics).recordCacheMiss("bootstrap", "/lookups/bootstrap");
    }
}
