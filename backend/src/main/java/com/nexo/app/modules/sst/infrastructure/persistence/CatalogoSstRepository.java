package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.CatalogoSst;
import com.nexo.app.modules.sst.domain.TipoCatalogoSst;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CatalogoSstRepository
        extends JpaRepository<CatalogoSst, UUID>, JpaSpecificationExecutor<CatalogoSst> {

    /** Entradas oferecidas no formulário: só as ativas do vocabulário, na ordem definida. */
    List<CatalogoSst> findByTipoAndAtivoTrueOrderByOrdemAscNomeAsc(TipoCatalogoSst tipo);

    Optional<CatalogoSst> findByTipoAndNomeNormalizadoAndAtivoTrue(
            TipoCatalogoSst tipo, String nomeNormalizado);

    /** Unicidade do nome dentro do vocabulário, considerando apenas o que está ativo. */
    boolean existsByTipoAndNomeNormalizadoAndAtivoTrue(TipoCatalogoSst tipo, String nomeNormalizado);

    boolean existsByTipoAndNomeNormalizadoAndAtivoTrueAndIdNot(
            TipoCatalogoSst tipo, String nomeNormalizado, UUID id);

    /**
     * Confirma que a entrada existe, está ativa e pertence ao vocabulário esperado — a
     * garantia que a chave estrangeira não dá quando os quatro catálogos dividem uma
     * tabela. É o que impede um evento de usar um local de lesão como classificação.
     */
    boolean existsByIdAndTipoAndAtivoTrue(UUID id, TipoCatalogoSst tipo);
}
