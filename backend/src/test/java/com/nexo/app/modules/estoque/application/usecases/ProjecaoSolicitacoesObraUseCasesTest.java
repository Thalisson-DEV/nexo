package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.MovimentacaoEstoque;
import com.nexo.app.modules.estoque.domain.TipoObraSnapshot;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MovimentacaoEstoqueRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjecaoSolicitacoesObraUseCasesTest {

    @Mock private SolicitacaoMaterialRepository repository;
    @Mock private ObraApi obraApi;
    @Mock private MovimentacaoEstoqueRepository movimentacaoRepository;
    @Mock private EstoqueRepository estoqueRepository;
    @Mock private Material material;
    @Mock private Estoque estoqueObra;
    @Mock private Estoque almoxarifado;
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
        when(itemPrimeiro.getMaterial()).thenReturn(material);
        when(itemSegundo.getMaterial()).thenReturn(material);
        when(itemPrimeiro.getQuantidade()).thenReturn(new BigDecimal("2.000"));
        when(itemPrimeiro.getQuantidadePendente()).thenReturn(new BigDecimal("1.000"));
        when(itemSegundo.getQuantidade()).thenReturn(new BigDecimal("3.000"));
        when(itemSegundo.getQuantidadePendente()).thenReturn(new BigDecimal("3.000"));
        var resposta = new ProjecaoSolicitacoesObraUseCases(repository, movimentacaoRepository, estoqueRepository, obraApi).buscar(idObra);

        assertThat(resposta.solicitacoes()).isEmpty();
        assertThat(resposta.ocorrencias()).extracting(item -> item.codigo())
                .containsExactly("OC 001", "OC 002");
        assertThat(resposta.ocorrencias().getFirst().solicitacoes()).singleElement()
                .satisfies(item -> assertThat(item.id()).isEqualTo(idSegunda));
    }

    @Test
    void incluiNasMovimentacoesOQueEntrouOuSaiuDoEstoqueDaObra() {
        UUID idObra = UUID.randomUUID();
        UUID idEstoque = UUID.randomUUID();
        ObraApi.ObraPublica obra = org.mockito.Mockito.mock(ObraApi.ObraPublica.class);
        when(obra.tipoObra()).thenReturn(ObraApi.TipoObraPublico.OBRA);
        when(obraApi.buscarPorId(idObra)).thenReturn(Optional.of(obra));
        when(repository.findAll(any(Specification.class))).thenReturn(List.of());
        when(estoqueObra.getId()).thenReturn(idEstoque);
        when(estoqueRepository.findByIdObra(idObra)).thenReturn(Optional.of(estoqueObra));
        UUID operacao = UUID.randomUUID();
        MovimentacaoEstoque linha = MovimentacaoEstoque.criarDevolucaoTecnica(estoqueObra, almoxarifado, material, new BigDecimal("2"),
                CondicaoMaterial.NOVO, new MovimentacaoEstoque.ContextoDevolucaoTecnica(TipoSolicitacaoTecnica.DEVOLUCAO_CONCRETO, idObra,
                        TipoObraSnapshot.OBRA, null, null, UUID.randomUUID()), UUID.randomUUID(), operacao, null);
        when(movimentacaoRepository.findDaObraOuDoEstoque(any(), any(), any())).thenReturn(List.of(linha));

        var resposta = new ProjecaoSolicitacoesObraUseCases(repository, movimentacaoRepository, estoqueRepository, obraApi).buscar(idObra);

        org.mockito.Mockito.verify(movimentacaoRepository).findDaObraOuDoEstoque(org.mockito.ArgumentMatchers.eq(idObra), org.mockito.ArgumentMatchers.eq(idEstoque), any());
        assertEquals(operacao, resposta.movimentacoes().getFirst().idReferencia());
    }

    @Test
    void trazOsItensDaSolicitacaoEAgrupaAsMovimentacoesPorOperacao() {
        UUID idObra = UUID.randomUUID();
        ObraApi.ObraPublica obra = org.mockito.Mockito.mock(ObraApi.ObraPublica.class);
        when(obra.tipoObra()).thenReturn(ObraApi.TipoObraPublico.OBRA);
        when(obraApi.buscarPorId(idObra)).thenReturn(Optional.of(obra));

        when(material.getCodigo()).thenReturn("MAT-1");
        when(material.getDescricao()).thenReturn("Cabo");
        when(material.getUnidadeMedida()).thenReturn("M");
        when(itemPrimeiro.getMaterial()).thenReturn(material);
        when(itemPrimeiro.getQuantidade()).thenReturn(new BigDecimal("5"));
        when(itemPrimeiro.getQuantidadePendente()).thenReturn(new BigDecimal("3"));
        when(itemPrimeiro.getQuantidadeEntregaLiquida()).thenReturn(new BigDecimal("2"));
        when(primeira.getItens()).thenReturn(List.of(itemPrimeiro));
        when(primeira.getTipoTecnico()).thenReturn(TipoSolicitacaoTecnica.ADICAO_COMUM);
        when(almoxarifado.getNomeIdentificador()).thenReturn("Central");
        when(primeira.getAlmoxarifadoAtendimento()).thenReturn(almoxarifado);
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(primeira));

        when(estoqueObra.getNomeIdentificador()).thenReturn("Obra");
        UUID operacao = UUID.randomUUID();
        MovimentacaoEstoque linha1 = MovimentacaoEstoque.criarDevolucaoTecnica(estoqueObra, almoxarifado, material, new BigDecimal("2"),
                CondicaoMaterial.NOVO, new MovimentacaoEstoque.ContextoDevolucaoTecnica(TipoSolicitacaoTecnica.DEVOLUCAO_CONCRETO, idObra,
                        TipoObraSnapshot.OBRA, null, null, UUID.randomUUID()), UUID.randomUUID(), operacao, null);
        MovimentacaoEstoque linha2 = MovimentacaoEstoque.criarDevolucaoTecnica(estoqueObra, almoxarifado, material, new BigDecimal("1"),
                CondicaoMaterial.SUCATA, new MovimentacaoEstoque.ContextoDevolucaoTecnica(TipoSolicitacaoTecnica.DEVOLUCAO_CONCRETO, idObra,
                        TipoObraSnapshot.OBRA, null, null, UUID.randomUUID()), UUID.randomUUID(), operacao, null);
        when(movimentacaoRepository.findByIdObraOrderByDataHoraDesc(any(), any())).thenReturn(List.of(linha1, linha2));

        var resposta = new ProjecaoSolicitacoesObraUseCases(repository, movimentacaoRepository, estoqueRepository, obraApi).buscar(idObra);

        var item = resposta.solicitacoes().getFirst().itens().getFirst();
        assertEquals("MAT-1", item.codigo());
        assertEquals(new BigDecimal("2"), item.quantidadeAtendida());
        assertEquals("Central", resposta.solicitacoes().getFirst().almoxarifadoNome());

        assertEquals(1, resposta.movimentacoes().size());
        var movimentacao = resposta.movimentacoes().getFirst();
        assertEquals(operacao, movimentacao.idReferencia());
        assertEquals(TipoSolicitacaoTecnica.DEVOLUCAO_CONCRETO, movimentacao.tipoTecnico());
        assertEquals("Obra", movimentacao.origemNome());
        assertEquals(2, movimentacao.itens().size());
        assertEquals(CondicaoMaterial.SUCATA, movimentacao.itens().get(1).condicao());
        assertEquals(false, resposta.movimentacoesTruncadas());
    }
}
