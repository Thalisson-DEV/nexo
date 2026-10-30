package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.ColaboradorAtivoResumo;
import com.nexo.app.modules.rh.application.mappers.ColaboradorMapper;
import com.nexo.app.modules.rh.domain.BaseOperacional;
import com.nexo.app.modules.rh.domain.Colaborador;
import com.nexo.app.modules.rh.domain.Funcao;
import com.nexo.app.modules.rh.infrastructure.persistence.BaseOperacionalRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.ColaboradorRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.FuncaoRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.ProcessoNegocioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ColaboradorUseCasesEscopoTest {

    @Mock ColaboradorRepository repository;
    @Mock ProcessoNegocioRepository processoRepository;
    @Mock BaseOperacionalRepository baseRepository;
    @Mock FuncaoRepository funcaoRepository;
    @Mock ColaboradorMapper mapper;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks ColaboradorUseCases useCases;

    @Test
    void listaBasesEFuncoesEmUmaConsultaSemCpf() {
        UUID idBase = UUID.randomUUID();
        UUID idFuncao = UUID.randomUUID();
        UUID idColaborador = UUID.randomUUID();
        Colaborador colaborador = mock(Colaborador.class);
        BaseOperacional base = mock(BaseOperacional.class);
        Funcao funcao = mock(Funcao.class);
        when(colaborador.getId()).thenReturn(idColaborador);
        when(colaborador.getNomeCompleto()).thenReturn("Pessoa selecionável");
        when(colaborador.getBaseOperacional()).thenReturn(base);
        when(colaborador.getFuncao()).thenReturn(funcao);
        when(base.getId()).thenReturn(idBase);
        when(base.getNome()).thenReturn("Base A");
        when(funcao.getId()).thenReturn(idFuncao);
        when(funcao.getNome()).thenReturn("Eletricista");
        when(repository.buscarAtivosPorBasesEFuncoes(Set.of(idBase), Set.of(idFuncao)))
                .thenReturn(List.of(colaborador));

        List<ColaboradorAtivoResumo> resultado = useCases.listarAtivosPorEscopo(
                Set.of(idBase), Set.of(idFuncao));

        assertEquals(1, resultado.size());
        assertEquals(idColaborador, resultado.getFirst().id());
        verify(repository).buscarAtivosPorBasesEFuncoes(Set.of(idBase), Set.of(idFuncao));
        verify(repository, never()).buscarAtivosPorBases(Set.of(idBase));
        verify(repository, never()).buscarAtivosPorFuncoes(Set.of(idFuncao));
        assertFalse(java.util.Arrays.stream(ColaboradorAtivoResumo.class.getRecordComponents())
                .anyMatch(campo -> campo.getName().toLowerCase().contains("cpf")));
    }

    @Test
    void escopoVazioNaoCarregaTodaAEmpresa() {
        assertEquals(List.of(), useCases.listarAtivosPorEscopo(null, List.of()));
        verifyNoInteractions(repository);
    }
}
