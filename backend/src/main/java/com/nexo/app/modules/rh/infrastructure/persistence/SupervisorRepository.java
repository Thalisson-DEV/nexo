package com.nexo.app.modules.rh.infrastructure.persistence;

import com.nexo.app.modules.rh.domain.Supervisor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupervisorRepository extends JpaRepository<Supervisor, UUID>, JpaSpecificationExecutor<Supervisor> {

    boolean existsByColaboradorIdAndAtivoTrue(UUID idColaborador);

    boolean existsByColaboradorIdAndCoordenadorIdAndAtivoTrue(UUID idColaborador, UUID idCoordenador);

    boolean existsByColaboradorIdAndAtivoTrueAndIdNot(UUID idColaborador, UUID id);

    Optional<Supervisor> findByColaboradorIdAndAtivoTrue(UUID idColaborador);

    @Query("""
            select s from Supervisor s
            join s.colaborador col
            where col.nomeCompletoHash = :nomeHash and s.ativo = true
            """)
    Optional<Supervisor> findByColaboradorNomeCompletoHashAndAtivoTrue(@Param("nomeHash") String nomeHash);

    @Query("""
            select s from Supervisor s
            join fetch s.colaborador col
            join fetch col.funcao
            join fetch col.baseOperacional
            join fetch col.processo
            join fetch s.coordenador coord
            join fetch coord.colaborador coordCol
            where s.id = :id
            """)
    Optional<Supervisor> buscarPorIdComDetalhes(@Param("id") UUID id);

    @Query("""
            select s from Supervisor s
            join fetch s.colaborador col
            join fetch s.coordenador coord
            join fetch coord.colaborador coordCol
            where s.ativo = true
            order by col.nomeBusca
            """)
    List<Supervisor> listarAtivos();
}
