package com.nexo.app.modules.notificacao.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexo.app.modules.notificacao.domain.InteracaoComunicado;

public interface InteracaoComunicadoRepository extends JpaRepository<InteracaoComunicado, UUID> {

    Optional<InteracaoComunicado> findByComunicadoIdAndIdUsuario(UUID idComunicado, UUID idUsuario);

    List<InteracaoComunicado> findByComunicadoIdAndAtivoTrue(UUID idComunicado);

    /**
     * Interações do usuário nos comunicados informados, para anexar o estado
     * individual ao mural sem uma consulta por comunicado.
     */
    List<InteracaoComunicado> findByIdUsuarioAndComunicadoIdIn(UUID idUsuario, List<UUID> idsComunicado);
}
