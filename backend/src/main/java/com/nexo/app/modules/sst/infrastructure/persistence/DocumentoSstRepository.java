package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.DocumentoSst;
import com.nexo.app.modules.sst.domain.SituacaoConferenciaDocumentoSst;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentoSstRepository
        extends JpaRepository<DocumentoSst, UUID>, JpaSpecificationExecutor<DocumentoSst> {

    Optional<DocumentoSst> findByIdAndAtivoTrue(UUID id);

    boolean existsByCategoriaCodigoAndIdBaseAndEscopoTitularAndIdTitularAndSha256AndAtivoTrueAndDataArquivamentoIsNull(
            String categoriaCodigo, UUID idBase,
            com.nexo.app.modules.sst.domain.EscopoTitularDocumentoSst escopoTitular,
            UUID idTitular, String sha256);

    List<DocumentoSst> findAllByIdDocumentoSubstituidoOrderByDataCriacaoAsc(UUID idDocumentoSubstituido);

    List<DocumentoSst> findAllByCategoriaCodigoAndIdBaseAndEscopoTitularAndIdTitularOrderByDataCriacaoAsc(
            String categoriaCodigo, UUID idBase,
            com.nexo.app.modules.sst.domain.EscopoTitularDocumentoSst escopoTitular,
            UUID idTitular);

    @Query("""
            select d from DocumentoSst d
            where d.idColaborador in :idsColaboradores
              and d.idTreinamento in :idsTreinamentos
              and d.situacaoConferencia = :situacao
              and d.atual = true
              and d.ativo = true
              and d.dataArquivamento is null
            """)
    List<DocumentoSst> buscarCertificadosElegiveis(
            @Param("idsColaboradores") Collection<UUID> idsColaboradores,
            @Param("idsTreinamentos") Collection<UUID> idsTreinamentos,
            @Param("situacao") SituacaoConferenciaDocumentoSst situacao);

    @Query("""
            select d from DocumentoSst d
            where d.situacaoConferencia = :situacao
              and d.atual = true
              and d.ativo = true
              and d.dataArquivamento is null
              and d.dataValidade is not null
              and d.dataInicioAlerta is not null
              and d.dataInicioAlerta <= :dataReferencia
            """)
    List<DocumentoSst> buscarDocumentosParaAlerta(
            @Param("situacao") SituacaoConferenciaDocumentoSst situacao,
            @Param("dataReferencia") LocalDate dataReferencia);
}
