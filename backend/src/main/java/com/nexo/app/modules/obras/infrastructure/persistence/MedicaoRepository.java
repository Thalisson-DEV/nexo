package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.Medicao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MedicaoRepository extends JpaRepository<Medicao, UUID>, JpaSpecificationExecutor<Medicao> {

    /** Valor medido (só medições geradas) de uma obra numa competência. */
    record MedidoNaCompetencia(UUID idObra, LocalDate competencia, BigDecimal valor, long medicoes) {
    }

    @Query(value = "select nextval('medicoes_obras_numero_seq')", nativeQuery = true)
    long proximoNumero();

    @Query("""
            select distinct m from Medicao m left join fetch m.itens
            where m.id = :id and m.ativo = true
            """)
    Optional<Medicao> buscarComItens(@Param("id") UUID id);

    @Query("""
            select distinct m from Medicao m left join fetch m.itens
            where m.idObra = :idObra and m.ativo = true
            order by m.numero desc
            """)
    List<Medicao> listarDaObraComItens(@Param("idObra") UUID idObra);

    @Query("""
            select new com.nexo.app.modules.obras.infrastructure.persistence.MedicaoRepository$MedidoNaCompetencia(
                m.idObra, m.competencia, sum(m.valorTotal), count(m))
            from Medicao m
            where m.ativo = true and m.idObra in :ids
              and m.status = com.nexo.app.modules.obras.domain.StatusMedicao.GERADA
            group by m.idObra, m.competencia
            """)
    List<MedidoNaCompetencia> somarPorCompetencia(@Param("ids") Collection<UUID> ids);

    @Query("select distinct m.idObra from Medicao m where m.ativo = true")
    List<UUID> obrasComMedicao();
}
