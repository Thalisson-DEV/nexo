package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ContextoOrganizacional;
import com.nexo.app.modules.sst.application.dto.InclusaoMembroCorpoSstRequest;
import com.nexo.app.modules.sst.application.dto.MembroCorpoSstRequest;
import com.nexo.app.modules.sst.application.mappers.BaseAbrangidaCorpoSstMapper;
import com.nexo.app.modules.sst.application.mappers.CorpoGovernancaSstMapper;
import com.nexo.app.modules.sst.application.mappers.MembroCorpoSstMapper;
import com.nexo.app.modules.sst.domain.ConflitoGovernancaSstException;
import com.nexo.app.modules.sst.domain.CorpoGovernancaSst;
import com.nexo.app.modules.sst.domain.MembroCorpoSst;
import com.nexo.app.modules.sst.domain.OrigemMembroCipa;
import com.nexo.app.modules.sst.domain.PapelMembroGovernancaSst;
import com.nexo.app.modules.sst.domain.RegimeComposicaoSst;
import com.nexo.app.modules.sst.domain.RepresentacaoCipa;
import com.nexo.app.modules.sst.domain.TitularidadeCipa;
import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import com.nexo.app.modules.sst.infrastructure.persistence.CorpoGovernancaSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.MembroCorpoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.PerfilNormativoGovernancaSstRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorpoGovernancaSstUseCasesTest {

    private static final UUID BASE = UUID.randomUUID();
    private static final UUID CORPO = UUID.randomUUID();
    private static final UUID COLABORADOR = UUID.randomUUID();
    private static final LocalDate INICIO = LocalDate.of(2026, 1, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 12, 31);

    @Mock CorpoGovernancaSstRepository repository;
    @Mock MembroCorpoSstRepository membroRepository;
    @Mock PerfilNormativoGovernancaSstRepository perfilRepository;
    @Mock BaseOperacionalApi baseOperacionalApi;
    @Mock ColaboradorApi colaboradorApi;
    @Mock CorpoGovernancaSstMapper mapper;
    @Mock MembroCorpoSstMapper membroMapper;
    @Mock BaseAbrangidaCorpoSstMapper baseMapper;

    @InjectMocks CorpoGovernancaSstUseCases useCases;

    @Test
    void inclusaoCongelaNomeFuncaoEBaseObtidosDoRhEmLote() {
        CorpoGovernancaSst corpo = corpoEmPreparacao(3L);
        UUID funcao = UUID.randomUUID();
        when(repository.findByIdAndAtivoTrue(CORPO)).thenReturn(Optional.of(corpo));
        when(colaboradorApi.buscarContextoOrganizacional(Set.of(COLABORADOR))).thenReturn(Map.of(
                COLABORADOR, new ContextoOrganizacional(COLABORADOR, "Nome oficial",
                        funcao, "Função oficial", BASE, "Base oficial", UUID.randomUUID(),
                        "Centro", UUID.randomUUID(), "Processo", true)));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        MembroCorpoSstRequest membro = new MembroCorpoSstRequest(
                COLABORADOR, PapelMembroGovernancaSst.MEMBRO,
                RepresentacaoCipa.EMPREGADOR, TitularidadeCipa.SUPLENTE,
                OrigemMembroCipa.DESIGNADO, null, null, "ATO-2026",
                false, INICIO, "Diurno");

        useCases.adicionarMembro(CORPO, new InclusaoMembroCorpoSstRequest(3L, membro));

        ArgumentCaptor<CorpoGovernancaSst> captor = ArgumentCaptor.forClass(CorpoGovernancaSst.class);
        verify(repository).save(captor.capture());
        MembroCorpoSst salvo = captor.getValue().getMembros().getFirst();
        assertEquals("Nome oficial", salvo.getNomeSnapshot());
        assertEquals("Função oficial", salvo.getFuncaoSnapshot());
        assertEquals("Base oficial", salvo.getBaseSnapshot());
        verify(colaboradorApi).buscarContextoOrganizacional(Set.of(COLABORADOR));
    }

    @Test
    void versaoObsoletaFalhaAntesDeConsultarRhOuMutarComposicao() {
        CorpoGovernancaSst corpo = corpoEmPreparacao(4L);
        when(repository.findByIdAndAtivoTrue(CORPO)).thenReturn(Optional.of(corpo));
        MembroCorpoSstRequest membro = new MembroCorpoSstRequest(
                COLABORADOR, PapelMembroGovernancaSst.MEMBRO,
                RepresentacaoCipa.EMPREGADOR, TitularidadeCipa.SUPLENTE,
                OrigemMembroCipa.DESIGNADO, null, null, "ATO-2026",
                false, INICIO, null);

        assertThrows(ConflitoGovernancaSstException.class, () -> useCases.adicionarMembro(
                CORPO, new InclusaoMembroCorpoSstRequest(3L, membro)));

        verify(colaboradorApi, never()).buscarContextoOrganizacional(any());
        verify(repository, never()).save(any());
    }

    private static CorpoGovernancaSst corpoEmPreparacao(long versao) {
        CorpoGovernancaSst corpo = new CorpoGovernancaSst(
                TipoCorpoGovernancaSst.CIPA, RegimeComposicaoSst.CIPA_DIMENSIONADA,
                "CIPA 2026", BASE, "Base oficial", "Estabelecimento A", "BA",
                INICIO, FIM, 2, null, false);
        ReflectionTestUtils.setField(corpo, "id", CORPO);
        ReflectionTestUtils.setField(corpo, "versao", versao);
        return corpo;
    }
}
