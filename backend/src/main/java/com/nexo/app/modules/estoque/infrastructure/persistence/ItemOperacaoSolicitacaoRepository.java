package com.nexo.app.modules.estoque.infrastructure.persistence;

import com.nexo.app.modules.estoque.domain.ItemOperacaoSolicitacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface ItemOperacaoSolicitacaoRepository extends JpaRepository<ItemOperacaoSolicitacao, UUID> {

    @Query("""
            select coalesce(sum(item.quantidade), 0)
            from ItemOperacaoSolicitacao item
            where item.itemOperacaoOriginal.id = :idItemOperacaoOriginal
            """)
    BigDecimal somarQuantidadeEstornadaPorItemOriginal(
            @Param("idItemOperacaoOriginal") UUID idItemOperacaoOriginal);
}
