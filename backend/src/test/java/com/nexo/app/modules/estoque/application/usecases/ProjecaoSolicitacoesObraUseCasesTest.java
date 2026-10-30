package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.domain.ItemSolicitacao;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import com.nexo.app.modules.obras.ObraApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjecaoSolicitacoesObraUseCasesTest {

    @Mock private SolicitacaoMaterialRepository repository;
    @Mock private ObraApi obraApi;
    @Mock private SolicitacaoMaterial primeira;
    @Mock private SolicitacaoMaterial segunda;
    @Mock private ItemSolicitacao itemPrimeiro;
    @Mock private ItemSolicitacao itemSegundo;

    @Test
    void agrupaSolicitacoesTecnicasPorOcorrenciaCadastrada() {
        UUID idObra = UUID.randomUUID();
        UUID idPrimeira = UUID.randomUUID();
        UUID idSegunda = UUID.randomUUID();
        UUID idOcorrenciaPrimeira = UUID.randomUUID();
        UUID idOcorrenciaSegunda = UUID.randomUUID();
        var obra = new ObraApi.ObraPublica(idObra, "OBRA-1", "Corretiva", ObraApi.TipoObraPublico.CORRETIVA,
                true, UUID.randomUUID(), "PROC", true, UUID.randomUUID(), Set.of(), null, List.of());
        when(obraApi.buscarPorId(idObra)).thenReturn(Optional.of(obra));
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(primeira, segunda));
        when(primeira.getId()).thenReturn(idPrimeira);
        when(segunda.getId()).thenReturn(idSegunda);
        when(primeira.getOcorrencia()).thenReturn("OC 002");
        when(segunda.getOcorrencia()).thenReturn("OC 001");
        when(primeira.getIdOcorrencia()).thenReturn(idOcorrenciaPrimeira);
        when(segunda.getIdOcorrencia()).thenReturn(idOcorrenciaSegunda);
        when(primeira.getTipoTecnico()).thenReturn(TipoSolicitacaoTecnica.ADICAO_COMUM);
        when(segunda.getTipoTecnico()).thenReturn(TipoSolicitacaoTecnica.DEVOLUCAO_COMUM);
        when(primeira.getItens()).thenReturn(List.of(itemPrimeiro));
        when(segunda.getItens()).thenReturn(List.of(itemSegundo));
        when(itemPrimeiro.getQuantidade()).thenReturn(new BigDecimal("2.000"));
        when(itemPrimeiro.getQuantidadePendente()).thenReturn(new BigDecimal("1.000"));
        when(itemSegundo.getQuantidade()).thenReturn(new BigDecimal("3.000"));
        when(itemSegundo.getQuantidadePendente()).thenReturn(new BigDecimal("3.000"));
        var resposta = new ProjecaoSolicitacoesObraUseCases(repository, obraApi).buscar(idObra);

        assertThat(resposta.solicitacoes()).isEmpty();
        assertThat(resposta.ocorrencias()).extracting(item -> item.codigo())
                .containsExactly("OC 001", "OC 002");
        assertThat(resposta.ocorrencias().getFirst().solicitacoes()).singleElement()
                .satisfies(item -> assertThat(item.id()).isEqualTo(idSegunda));
    }
}
