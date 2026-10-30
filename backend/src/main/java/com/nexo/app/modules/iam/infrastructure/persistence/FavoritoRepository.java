package com.nexo.app.modules.iam.infrastructure.persistence;

import com.nexo.app.modules.iam.domain.Favorito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FavoritoRepository extends JpaRepository<Favorito, UUID> {

    /**
     * Busca todos os favoritos ativos de um determinado usuário.
     */
    List<Favorito> findByUsuarioIdAndAtivoTrueOrderByTituloAsc(UUID usuarioId);

    /**
     * Busca um favorito específico por ID e ID do usuário para garantir a posse.
     */
    Optional<Favorito> findByIdAndUsuarioIdAndAtivoTrue(UUID id, UUID usuarioId);
}
