package com.nexo.app.modules.financeiro.infrastructure.persistence;

import com.nexo.app.modules.financeiro.domain.FormularioSolicitacao;
import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FormularioSolicitacaoRepository
        extends JpaRepository<FormularioSolicitacao, UUID>, JpaSpecificationExecutor<FormularioSolicitacao> {

    Optional<FormularioSolicitacao> findByTipoSolicitacaoAndCodigo(TipoSolicitacao tipoSolicitacao, String codigo);

    boolean existsByTipoSolicitacaoAndCodigo(TipoSolicitacao tipoSolicitacao, String codigo);

    /** Variantes disponíveis ao solicitante na abertura de uma nova solicitação. */
    List<FormularioSolicitacao> findByTipoSolicitacaoAndAtivoTrueOrderByPadraoDescCodigoAsc(
            TipoSolicitacao tipoSolicitacao);

    /** Usada para destituir a variante padrão anterior quando outra assume o posto. */
    List<FormularioSolicitacao> findByTipoSolicitacaoAndPadraoTrue(TipoSolicitacao tipoSolicitacao);
}
