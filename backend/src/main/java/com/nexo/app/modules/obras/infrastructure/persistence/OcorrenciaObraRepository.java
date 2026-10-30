package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.OcorrenciaObra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OcorrenciaObraRepository extends JpaRepository<OcorrenciaObra, UUID> {

    Optional<OcorrenciaObra> findByObraIdAndCodigoNormalizado(UUID idObra, String codigoNormalizado);

    @Modifying
    @Query(value = "INSERT INTO ocorrencias_obras "
            + "(id, versao, data_criacao, usuario_criacao, ativo, id_obra, codigo_normalizado) "
            + "VALUES (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, :idObra, :codigo) "
            + "ON CONFLICT (id_obra, codigo_normalizado) DO NOTHING", nativeQuery = true)
    int inserirSeAusente(@Param("idObra") UUID idObra, @Param("codigo") String codigo);
}
