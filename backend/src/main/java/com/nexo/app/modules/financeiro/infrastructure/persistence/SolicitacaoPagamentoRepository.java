package com.nexo.app.modules.financeiro.infrastructure.persistence;

import com.nexo.app.modules.financeiro.domain.SolicitacaoPagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SolicitacaoPagamentoRepository
        extends JpaRepository<SolicitacaoPagamento, UUID>, JpaSpecificationExecutor<SolicitacaoPagamento> {

    Optional<SolicitacaoPagamento> findFirstByAtivoTrueOrderByIdAsc();

    Optional<SolicitacaoPagamento> findByNumero(Long numero);

    @Query("""
            select s from SolicitacaoPagamento s
            where s.nucleo.dadosPagamento is not null
              and s.nucleo.dadosPagamentoBusca is null
            """)
    java.util.List<SolicitacaoPagamento> buscarPendentesReindexacao(Pageable pageable);

    /** Pagamentos da compra que ainda dependem da aprovação dela. */
    @Query("""
            select s.numero from SolicitacaoPagamento s
            where s.nucleo.solicitacaoCompraId = :compraId
              and s.ativo = true
              and s.status in (com.nexo.app.modules.financeiro.domain.StatusSolicitacao.RASCUNHO,
                               com.nexo.app.modules.financeiro.domain.StatusSolicitacao.SOLICITADO,
                               com.nexo.app.modules.financeiro.domain.StatusSolicitacao.APROVADO)
            order by s.numero
            """)
    java.util.List<Long> numerosVinculadosEmAndamento(@Param("compraId") UUID compraId);
}
