package com.nexo.app.modules.financeiro.infrastructure.persistence;

import com.nexo.app.modules.financeiro.domain.DocumentoFinanceiro;
import com.nexo.app.modules.financeiro.domain.TipoDocumentoFinanceiro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentoFinanceiroRepository
        extends JpaRepository<DocumentoFinanceiro, UUID>, JpaSpecificationExecutor<DocumentoFinanceiro> {

    /** Documentos de uma solicitação, na ordem em que chegaram. */
    List<DocumentoFinanceiro> findByOrigemSolicitacaoIdAndAtivoTrueOrderByDataCriacaoAsc(UUID solicitacaoId);

    /**
     * Documentos de várias solicitações de uma vez. Existe para que a listagem de
     * solicitações não dispare uma consulta por linha só para exibir quantos
     * documentos cada uma tem.
     */
    List<DocumentoFinanceiro> findByOrigemSolicitacaoIdInAndAtivoTrueOrderByDataCriacaoAsc(
            Collection<UUID> solicitacaoIds);

    /**
     * Procura um documento ativo com o mesmo conteúdo. É o que permite avisar
     * "este arquivo já está no repositório" antes de gravar a segunda cópia.
     */
    Optional<DocumentoFinanceiro> findFirstByHashConteudoAndAtivoTrue(String hashConteudo);

    /**
     * Conteúdos que aparecem em mais de um documento ativo. Serve ao indicador de
     * duplicidade do dashboard — a conta é feita no banco porque interessa o
     * histórico inteiro, não só o recorte carregado na memória.
     */
    @Query("""
            select d.hashConteudo from DocumentoFinanceiro d
            where d.ativo = true and d.hashConteudo is not null
            group by d.hashConteudo having count(d.id) > 1
            """)
    List<String> buscarHashesDuplicados();

    /** Quantos documentos ativos cada solicitação acumulou. */
    interface ContagemPorOrigem {
        UUID getSolicitacaoId();

        long getQuantidade();
    }

    /**
     * Contagem de documentos por solicitação, em uma consulta agregada. O dashboard
     * precisa do número por linha de ranking, e trazer as entidades só para contá-las
     * carregaria milhares de registros que ninguém vai ler.
     */
    @Query("""
            select d.origem.solicitacaoId as solicitacaoId, count(d.id) as quantidade
            from DocumentoFinanceiro d
            where d.ativo = true and d.origem.solicitacaoId in :ids
            group by d.origem.solicitacaoId
            """)
    List<ContagemPorOrigem> contarPorOrigem(@Param("ids") Collection<UUID> ids);

    /** Solicitações que já possuem ao menos um documento fiscal ativo. */
    @Query("""
            select distinct d.origem.solicitacaoId from DocumentoFinanceiro d
            where d.ativo = true
              and d.origem.solicitacaoId in :ids
              and d.tipo in :tipos
            """)
    List<UUID> buscarOrigensComDocumentoDosTipos(@Param("ids") Collection<UUID> ids,
                                                 @Param("tipos") Collection<TipoDocumentoFinanceiro> tipos);
}
