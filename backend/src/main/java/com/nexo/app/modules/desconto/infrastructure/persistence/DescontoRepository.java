package com.nexo.app.modules.desconto.infrastructure.persistence;

import com.nexo.app.modules.desconto.domain.Desconto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nexo.app.modules.desconto.domain.StatusDesconto;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface DescontoRepository extends JpaRepository<Desconto, UUID>, JpaSpecificationExecutor<Desconto> {

    java.util.Optional<Desconto> findFirstByAtivoTrueOrderByIdAsc();

    @Query("""
            select distinct tipo.id as idTipoEquipamento,
                            tipo.descricao as tipoEquipamento,
                            d.id as idDesconto,
                            d.status as status
              from Desconto d
              join d.itens item
              join item.tipoEquipamento tipo
             where d.ativo = true
               and item.ativo = true
               and d.status in (com.nexo.app.modules.desconto.domain.StatusDesconto.BAIXADO,
                                com.nexo.app.modules.desconto.domain.StatusDesconto.REJEITADO)
               and d.dataOcorrencia >= :inicio
               and d.dataOcorrencia < :fim
            """)
    List<ResultadoFinalPorTipoProjection> listarResultadosFinaisDistintosPorTipo(
            @Param("inicio") OffsetDateTime inicio,
            @Param("fim") OffsetDateTime fim);

    /**
     * IDs, entre os informados, que já possuem ao menos um anexo ativo.
     *
     * <p>Existe porque o dashboard precisa saber quais pendências estão sem comprovação,
     * e {@code anexos} não pode viajar no mesmo {@code join fetch} dos itens — duas
     * coleções na mesma consulta produzem o produto cartesiano delas. Uma consulta
     * enxuta de identificadores resolve a pergunta sem materializar anexo nenhum.</p>
     */
    @Query("""
            select distinct a.desconto.id
              from DescontoAnexo a
             where a.ativo = true
               and a.desconto.id in :ids
            """)
    List<UUID> buscarIdsComAnexoAtivo(@Param("ids") Collection<UUID> ids);

    interface ResultadoFinalPorTipoProjection {
        UUID getIdTipoEquipamento();
        String getTipoEquipamento();
        UUID getIdDesconto();
        StatusDesconto getStatus();
    }
}
