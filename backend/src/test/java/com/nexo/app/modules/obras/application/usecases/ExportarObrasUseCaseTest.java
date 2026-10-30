package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.StatusObra;
import com.nexo.app.modules.obras.domain.TipoObra;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.rh.CoordenadorApi;
import com.nexo.app.modules.rh.SupervisorApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.io.StringWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportarObrasUseCaseTest {

    @Mock
    private ObraUseCases obraUseCases;
    @Mock
    private ObraRepository obraRepository;
    @Mock
    private SupervisorApi supervisorApi;
    @Mock
    private CoordenadorApi coordenadorApi;
    @Mock
    private RelacionamentoProjetoUseCases relacionamentoProjetoUseCases;

    @Test
    void deveExportarTipoOperacionalDaObra() throws Exception {
        UUID supervisorId = UUID.randomUUID();
        UUID coordenadorId = UUID.randomUUID();
        Obra obra = new Obra(
                "1000002", "MANUTENCAO CORRETIVA", null, null, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null, null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                supervisorId, coordenadorId, new StatusObra("PLANEJADA", 1, false),
                null, null, TipoObra.CORRETIVA
        );
        when(obraRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(obra)));
        when(supervisorApi.buscarPorIdResponse(supervisorId)).thenReturn(Optional.empty());
        when(coordenadorApi.buscarPorIdResponse(coordenadorId)).thenReturn(Optional.empty());

        StringWriter writer = new StringWriter();
        new ExportarObrasUseCase(obraUseCases, obraRepository, supervisorApi, coordenadorApi,
                relacionamentoProjetoUseCases)
                .exportarDetalhado(null, writer);

        assertThat(writer.toString())
                .contains("Tipo Obra", "Projetos Relacionados", "CORRETIVA")
                .doesNotContain("Obra Principal", "Subprojetos");
    }
}
