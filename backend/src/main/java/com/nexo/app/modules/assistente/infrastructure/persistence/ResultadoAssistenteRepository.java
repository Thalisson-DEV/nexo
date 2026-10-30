package com.nexo.app.modules.assistente.infrastructure.persistence;

import com.nexo.app.modules.assistente.domain.ResultadoAssistente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResultadoAssistenteRepository extends JpaRepository<ResultadoAssistente, UUID> {

    Optional<ResultadoAssistente> findByConversaIdAndCodigo(UUID conversaId, String codigo);

    List<ResultadoAssistente> findByConversaIdOrderByDataCriacaoAsc(UUID conversaId);

    long countByConversaId(UUID conversaId);

    /** Metadados leves (sem linhas) para montar o estado do workspace no prompt. */
    @Query("""
            select new com.nexo.app.modules.assistente.infrastructure.persistence.ResumoResultadoAssistente(
                r.codigo, r.titulo, r.origem, r.natureza, r.colunasJson, r.totalLinhas, r.truncado, r.dataCriacao)
            from ResultadoAssistente r where r.conversaId = :conversaId order by r.dataCriacao asc
            """)
    List<ResumoResultadoAssistente> listarResumos(@Param("conversaId") UUID conversaId);
}
