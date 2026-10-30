package com.nexo.app.modules.financeiro.infrastructure.persistence;

import com.nexo.app.modules.financeiro.domain.SolicitacaoCompra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SolicitacaoCompraRepository
        extends JpaRepository<SolicitacaoCompra, UUID>, JpaSpecificationExecutor<SolicitacaoCompra> {

    /** Busca pelo número sequencial, que é como as equipes se referem à solicitação. */
    Optional<SolicitacaoCompra> findByNumero(Long numero);

    @Query("""
            select s from SolicitacaoCompra s
            where s.nucleo.dadosPagamento is not null
              and s.nucleo.dadosPagamentoBusca is null
            """)
    java.util.List<SolicitacaoCompra> buscarPendentesReindexacao(Pageable pageable);
}
