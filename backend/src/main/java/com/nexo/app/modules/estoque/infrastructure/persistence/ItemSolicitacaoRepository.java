package com.nexo.app.modules.estoque.infrastructure.persistence;

import com.nexo.app.modules.estoque.domain.ItemSolicitacao;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ItemSolicitacaoRepository extends JpaRepository<ItemSolicitacao, UUID> {

    /** Itens de solicitações comerciais anteriores do estoque que tiveram entrega líquida, da mais recente para a mais antiga. */
    @Query("""
            SELECT i FROM ItemSolicitacao i JOIN FETCH i.solicitacao s
            WHERE s.estoqueDestino.id = :idEstoque
              AND s.contexto = com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial.COMERCIAL
              AND s.ativo = true
              AND s.id <> :idAtual
              AND s.dataSolicitacao < :antesDe
              AND i.material.id IN :materiais
              AND i.quantidadeEntregueBruta > i.quantidadeEntregaEstornada
            ORDER BY s.dataSolicitacao DESC
            """)
    List<ItemSolicitacao> findEntreguesAnteriores(
            @Param("idEstoque") UUID idEstoque,
            @Param("idAtual") UUID idAtual,
            @Param("antesDe") OffsetDateTime antesDe,
            @Param("materiais") Collection<UUID> materiais,
            Pageable pageable);
}
