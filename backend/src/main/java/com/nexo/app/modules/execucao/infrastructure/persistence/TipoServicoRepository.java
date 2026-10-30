package com.nexo.app.modules.execucao.infrastructure.persistence;

import com.nexo.app.modules.execucao.domain.TipoServico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TipoServicoRepository extends JpaRepository<TipoServico, UUID>, JpaSpecificationExecutor<TipoServico> {

    Optional<TipoServico> findByDescricaoNormalizadaAndAtivoTrue(String descricaoNormalizada);

    @Query("SELECT t FROM TipoServico t WHERE t.ativo = true ORDER BY t.descricao ASC")
    List<TipoServico> listarAtivosOrdenados();
}
