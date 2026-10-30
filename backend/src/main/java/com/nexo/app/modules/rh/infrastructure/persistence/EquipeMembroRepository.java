package com.nexo.app.modules.rh.infrastructure.persistence;

import com.nexo.app.modules.rh.domain.EquipeMembro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EquipeMembroRepository extends JpaRepository<EquipeMembro, UUID> {

    /**
     * Busca o vínculo ativo de um colaborador em QUALQUER equipe.
     */
    @Query("SELECT em FROM EquipeMembro em WHERE em.colaborador.id = :colaboradorId AND em.dataSaida IS NULL")
    Optional<EquipeMembro> findVincuoAtivoPorColaborador(@Param("colaboradorId") UUID colaboradorId);

    /**
     * Verifica se o colaborador possui vínculo ativo com uma equipe específica e ativa.
     */
    @Query("SELECT COUNT(em) > 0 FROM EquipeMembro em WHERE em.equipe.id = :idEquipe AND em.colaborador.id = :idColaborador AND em.dataSaida IS NULL AND em.colaborador.ativo = true AND em.equipe.ativo = true")
    boolean isMembroAtivo(@Param("idEquipe") UUID idEquipe, @Param("idColaborador") UUID idColaborador);

    /**
     * Vínculos ativos dos colaboradores informados, com a equipe já carregada,
     * para exibir em qual equipe cada um está hoje sem consultas por linha.
     */
    @Query("SELECT em FROM EquipeMembro em JOIN FETCH em.equipe e " +
           "WHERE em.colaborador.id IN :idsColaboradores AND em.dataSaida IS NULL AND e.ativo = true")
    List<EquipeMembro> findVinculosAtivosPorColaboradores(@Param("idsColaboradores") Collection<UUID> idsColaboradores);
}
