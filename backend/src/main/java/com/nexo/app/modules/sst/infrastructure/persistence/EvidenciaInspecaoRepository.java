package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.EvidenciaInspecao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface EvidenciaInspecaoRepository extends JpaRepository<EvidenciaInspecao, UUID> {

    /**
     * Fotos enviadas que nunca chegaram a virar inspeção — o inspetor desistiu do
     * lançamento, trocou de aparelho ou o rascunho se perdeu. Base do expurgo.
     */
    List<EvidenciaInspecao> findByInspecaoIsNullAndDataCriacaoBefore(OffsetDateTime corte);
}
