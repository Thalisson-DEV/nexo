package com.nexo.app.modules.iam.infrastructure.persistence;

import com.nexo.app.modules.iam.domain.SessaoAutenticacao;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessaoAutenticacaoRepository extends JpaRepository<SessaoAutenticacao, UUID> {

    /**
     * Trava a linha durante a renovação. Duas renovações simultâneas do mesmo aparelho
     * (duas abas, ou a retentativa chegando antes da resposta original) precisam ser
     * serializadas: a segunda tem de enxergar a rotação feita pela primeira.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SessaoAutenticacao s WHERE s.id = :id")
    Optional<SessaoAutenticacao> buscarParaRenovar(@Param("id") UUID id);

    @Query("SELECT s FROM SessaoAutenticacao s WHERE s.usuarioId = :usuarioId AND s.revogadaEm IS NULL")
    List<SessaoAutenticacao> buscarAtivasDoUsuario(@Param("usuarioId") UUID usuarioId);

    @Modifying
    @Query("DELETE FROM SessaoAutenticacao s WHERE s.expiraEm < :corte OR s.revogadaEm < :corte")
    int expurgarEncerradasAntesDe(@Param("corte") OffsetDateTime corte);
}
