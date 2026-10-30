package com.nexo.app.modules.execucao.application.usecases;

import com.nexo.app.modules.contrato.AcaoResponse;
import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.estoque.EstoqueApi;
import com.nexo.app.modules.estoque.MaterialResponse;
import com.nexo.app.modules.execucao.application.dto.ExecucaoComercialResponse;
import com.nexo.app.modules.execucao.application.dto.ExecucaoFilter;
import com.nexo.app.modules.execucao.application.dto.TipoServicoResponse;
import com.nexo.app.modules.execucao.application.mappers.ExecucaoComercialMapper;
import com.nexo.app.modules.execucao.domain.ExecucaoAcao;
import com.nexo.app.modules.execucao.domain.ExecucaoComercial;
import com.nexo.app.modules.execucao.domain.ExecucaoMaterial;
import com.nexo.app.modules.execucao.infrastructure.persistence.ExecucaoComercialRepository;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.shared.application.dto.PageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecucaoComercialListagemTest {

    @Mock ExecucaoComercialRepository repository;
    @Mock ExecucaoComercialMapper mapper;
    @Mock EquipeApi equipeApi;
    @Mock ContratoApi contratoApi;
    @Mock EstoqueApi estoqueApi;
    @Mock TipoServicoUseCases tipoServicoUseCases;
    @InjectMocks ExecucaoComercialUseCases useCases;

    private final UUID idEquipe = UUID.randomUUID();
    private final UUID idAcao = UUID.randomUUID();
    private final UUID idMaterial = UUID.randomUUID();
    private final UUID idTipo = UUID.randomUUID();

    @Test
    @SuppressWarnings("unchecked")
    void montaAPaginaComEquipesAcoesEMateriaisBuscadosDeUmaVez() {
        ExecucaoComercial primeira = nota();
        ExecucaoComercial segunda = nota();
        Pageable pagina = PageRequest.of(0, 20);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(primeira, segunda), pagina, 2));
        when(mapper.toResponse(any(ExecucaoComercial.class))).thenAnswer(inv -> respostaBase());

        EquipeResponse equipe = comId(mock(EquipeResponse.class), idEquipe);
        AcaoResponse acao = mock(AcaoResponse.class);
        when(acao.id()).thenReturn(idAcao);
        MaterialResponse material = mock(MaterialResponse.class);
        when(material.id()).thenReturn(idMaterial);
        TipoServicoResponse tipo = new TipoServicoResponse(idTipo, "CL", "LIGAÇÃO NOVA", true, null, null);
        when(equipeApi.buscarPorIdsResponse(anyCollection())).thenReturn(List.of(equipe));
        when(contratoApi.buscarAcoesPorIdsResponse(anyCollection())).thenReturn(List.of(acao));
        when(estoqueApi.buscarMateriaisPorIdsResponse(anyCollection())).thenReturn(List.of(material));
        when(tipoServicoUseCases.listarTodos()).thenReturn(List.of(tipo));

        PageResponse<ExecucaoComercialResponse> resultado = useCases.listar(filtroVazio(), pagina);

        assertEquals(2, resultado.getContent().size());
        for (ExecucaoComercialResponse nota : resultado.getContent()) {
            assertSame(equipe, nota.equipe());
            assertSame(tipo, nota.tipoServico());
            assertSame(acao, nota.acoes().getFirst().acao());
            assertSame(material, nota.materiais().getFirst().material());
            assertEquals(new BigDecimal("2"), nota.materiais().getFirst().quantidade());
            assertNull(nota.municipio());
        }
        verify(equipeApi).buscarPorIdsResponse(List.of(idEquipe));
        verify(contratoApi).buscarAcoesPorIdsResponse(List.of(idAcao));
        verify(estoqueApi).buscarMateriaisPorIdsResponse(List.of(idMaterial));
        verify(equipeApi, never()).buscarPorIdResponse(any());
        verify(contratoApi, never()).buscarAcaoPorIdResponse(any());
        verify(estoqueApi, never()).buscarMaterialPorIdResponse(any());
    }

    private ExecucaoComercial nota() {
        ExecucaoAcao acao = mock(ExecucaoAcao.class);
        when(acao.getId()).thenReturn(UUID.randomUUID());
        when(acao.getIdAcao()).thenReturn(idAcao);
        ExecucaoMaterial material = mock(ExecucaoMaterial.class);
        when(material.getId()).thenReturn(UUID.randomUUID());
        when(material.getIdMaterial()).thenReturn(idMaterial);
        when(material.getQuantidade()).thenReturn(new BigDecimal("2"));
        ExecucaoComercial nota = mock(ExecucaoComercial.class);
        when(nota.getIdEquipe()).thenReturn(idEquipe);
        when(nota.getIdTipoServico()).thenReturn(idTipo);
        when(nota.getAcoes()).thenReturn(List.of(acao));
        when(nota.getMateriais()).thenReturn(List.of(material));
        return nota;
    }

    private static EquipeResponse comId(EquipeResponse equipe, UUID id) {
        when(equipe.id()).thenReturn(id);
        return equipe;
    }

    private static ExecucaoComercialResponse respostaBase() {
        return new ExecucaoComercialResponse(UUID.randomUUID(), "1234567", null, null, null, null, null, null,
                null, null, null, null, null, List.of(), List.of(), null, null, null, 0);
    }

    private static ExecucaoFilter filtroVazio() {
        return new ExecucaoFilter(null, null, null, null, null, null, null, false, null, null, null, null, null,
                null, null, null, null, null, null);
    }
}
