package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.AlertaValidadeDocumentoSst;
import com.nexo.app.modules.sst.domain.SituacaoAlertaDocumentoSst;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface AlertaValidadeDocumentoSstRepository
        extends JpaRepository<AlertaValidadeDocumentoSst, UUID> {

    boolean existsByIdDocumentoAndDiasAntecedenciaAndDataValidade(
            UUID idDocumento, int diasAntecedencia, LocalDate dataValidade);

    List<AlertaValidadeDocumentoSst> findAllByIdDocumentoAndSituacao(
            UUID idDocumento, SituacaoAlertaDocumentoSst situacao);
}
