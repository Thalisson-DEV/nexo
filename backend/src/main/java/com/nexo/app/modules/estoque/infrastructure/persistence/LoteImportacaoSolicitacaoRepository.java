package com.nexo.app.modules.estoque.infrastructure.persistence;

import com.nexo.app.modules.estoque.domain.LoteImportacaoSolicitacao;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoteImportacaoSolicitacaoRepository
        extends JpaRepository<LoteImportacaoSolicitacao, UUID> {

    Optional<LoteImportacaoSolicitacao> findByTokenHashAndCriadoPor(String tokenHash, UUID criadoPor);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lote from LoteImportacaoSolicitacao lote "
            + "where lote.tokenHash = :tokenHash and lote.criadoPor = :criadoPor")
    Optional<LoteImportacaoSolicitacao> buscarParaConfirmacao(
            @Param("tokenHash") String tokenHash,
            @Param("criadoPor") UUID criadoPor
    );
}
