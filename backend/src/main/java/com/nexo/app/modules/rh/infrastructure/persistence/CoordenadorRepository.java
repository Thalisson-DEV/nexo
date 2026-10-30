package com.nexo.app.modules.rh.infrastructure.persistence;

import com.nexo.app.modules.rh.domain.Coordenador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CoordenadorRepository extends JpaRepository<Coordenador, UUID>, JpaSpecificationExecutor<Coordenador> {

    boolean existsByColaboradorIdAndAtivoTrue(UUID idColaborador);

    boolean existsByColaboradorIdAndAtivoTrueAndIdNot(UUID idColaborador, UUID id);

    Optional<Coordenador> findByColaboradorIdAndAtivoTrue(UUID idColaborador);

    @Query("""
            select c from Coordenador c
            join c.colaborador col
            where col.nomeCompletoHash = :nomeHash and c.ativo = true
            """)
    Optional<Coordenador> findByColaboradorNomeCompletoHashAndAtivoTrue(@Param("nomeHash") String nomeHash);

    @Query("""
            select c from Coordenador c
            join fetch c.colaborador col
            join fetch col.funcao
            join fetch col.baseOperacional
            join fetch col.processo
            where c.id = :id
            """)
    Optional<Coordenador> buscarPorIdComDetalhes(@Param("id") UUID id);

    @Query("""
            select c from Coordenador c
            join fetch c.colaborador col
            where c.ativo = true
            order by col.nomeBusca
            """)
    List<Coordenador> listarAtivos();
}
