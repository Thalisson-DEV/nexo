package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.domain.BaseOperacional;
import com.nexo.app.modules.rh.domain.Equipe;
import com.nexo.app.modules.rh.domain.ProcessoNegocio;
import com.nexo.app.modules.rh.infrastructure.persistence.EquipeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipeApiPublicaTest {

    @Mock
    private EquipeRepository repository;

    @InjectMocks
    private EquipeUseCases equipeApi;

    @Test
    void deveResolverEquipeVinculadaAtivaComSnapshotSomentePublico() {
        UUID idEquipe = UUID.randomUUID();
        UUID idBase = UUID.randomUUID();
        UUID idProcesso = UUID.randomUUID();
        UUID idUsuario = UUID.randomUUID();
        Equipe equipe = mock(Equipe.class);
        BaseOperacional base = mock(BaseOperacional.class);
        ProcessoNegocio processo = mock(ProcessoNegocio.class);
        when(equipe.getId()).thenReturn(idEquipe);
        when(equipe.getCodigo()).thenReturn("EQ-01");
        when(equipe.isAtivo()).thenReturn(true);
        when(equipe.getBaseOperacional()).thenReturn(base);
        when(equipe.getProcesso()).thenReturn(processo);
        when(equipe.getIdUsuario()).thenReturn(idUsuario);
        when(base.getId()).thenReturn(idBase);
        when(processo.getId()).thenReturn(idProcesso);
        when(repository.findById(idEquipe)).thenReturn(Optional.of(equipe));

        EquipeApi.EquipePublica snapshot = equipeApi
                .buscarAtivaSeVinculada(idEquipe, Set.of(idEquipe))
                .orElseThrow();

        assertThat(snapshot.id()).isEqualTo(idEquipe);
        assertThat(snapshot.codigo()).isEqualTo("EQ-01");
        assertThat(snapshot.ativa()).isTrue();
        assertThat(snapshot.idBaseOperacional()).isEqualTo(idBase);
        assertThat(snapshot.idProcesso()).isEqualTo(idProcesso);
        assertThat(snapshot.idUsuarioResponsavel()).isEqualTo(idUsuario);
    }

    @Test
    void deveRejeitarEquipeNaoVinculadaSemConsultarRepositorio() {
        UUID idEquipe = UUID.randomUUID();

        assertThat(equipeApi.buscarAtivaSeVinculada(idEquipe, Set.of(UUID.randomUUID()))).isEmpty();
        verify(repository, never()).findById(idEquipe);
    }

    @Test
    void deveRejeitarEquipeVinculadaPoremInativa() {
        UUID idEquipe = UUID.randomUUID();
        Equipe equipe = mock(Equipe.class);
        when(equipe.getId()).thenReturn(idEquipe);
        when(equipe.getCodigo()).thenReturn("EQ-02");
        when(equipe.isAtivo()).thenReturn(false);
        when(repository.findById(idEquipe)).thenReturn(Optional.of(equipe));

        assertThat(equipeApi.buscarAtivaSeVinculada(idEquipe, Set.of(idEquipe))).isEmpty();
    }
}
