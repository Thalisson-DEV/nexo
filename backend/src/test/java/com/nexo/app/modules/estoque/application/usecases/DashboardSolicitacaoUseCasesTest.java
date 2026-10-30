package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.DashboardSolicitacaoFilter;
import com.nexo.app.modules.estoque.infrastructure.persistence.DashboardSolicitacaoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.projections.DashboardSolicitacaoProjection;
import com.nexo.app.modules.estoque.infrastructure.persistence.projections.SegmentoSolicitacaoTecnicaProjection;
import com.nexo.app.modules.rh.EquipeApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardSolicitacaoUseCasesTest {

    @Mock private DashboardSolicitacaoRepository repository;
    @Mock private EquipeApi equipeApi;
    @InjectMocks private DashboardSolicitacaoUseCases useCases;

    @Test
    void mantemMetricasComerciaisEAcrescentaFilaTecnicaSegmentada() {
        DashboardSolicitacaoProjection comercial = org.mockito.Mockito.mock(DashboardSolicitacaoProjection.class);
        when(comercial.getTotalSolicitacoes()).thenReturn(7L);
        when(repository.obterMetricasDashboard(
                nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class),
                anyBoolean(), anyList())).thenReturn(comercial);
        when(repository.obterTopMateriais(
                nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class),
                anyBoolean(), anyList())).thenReturn(List.of());
        when(repository.obterTopEstoques(
                nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class),
                anyBoolean(), anyList())).thenReturn(List.of());
        when(repository.obterTopMotivos(
                nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class),
                anyBoolean(), anyList())).thenReturn(List.of());

        SegmentoSolicitacaoTecnicaProjection tecnico =
                org.mockito.Mockito.mock(SegmentoSolicitacaoTecnicaProjection.class);
        when(tecnico.getContexto()).thenReturn("TECNICO");
        when(tecnico.getTipoTecnico()).thenReturn("221");
        when(tecnico.getTipoObra()).thenReturn("CORRETIVA");
        when(tecnico.getOcorrencia()).thenReturn("OC-42");
        when(tecnico.getTotalSolicitacoes()).thenReturn(1L);
        when(tecnico.getTotalItens()).thenReturn(2L);
        when(tecnico.getQuantidadeSolicitada()).thenReturn(new BigDecimal("3.500"));
        when(repository.obterSegmentosTecnicos(
                nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class),
                nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class),
                nullable(String.class))).thenReturn(List.of(tecnico));

        var resposta = useCases.obterMetricas(new DashboardSolicitacaoFilter(
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null));

        assertThat(resposta.totalSolicitacoes()).isEqualTo(7);
        assertThat(resposta.segmentosTecnicos()).singleElement().satisfies(segmento -> {
            assertThat(segmento.tipoTecnico()).isEqualTo("221");
            assertThat(segmento.ocorrencia()).isEqualTo("OC-42");
            assertThat(segmento.quantidadeSolicitada()).isEqualByComparingTo("3.500");
        });
    }
}
