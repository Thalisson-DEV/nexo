package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.ItemPlanejado;
import com.nexo.app.modules.obras.domain.StatusItemPlanejado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ItemPlanejadoRepository extends JpaRepository<ItemPlanejado, UUID> {

    /** Serviços planejados de uma obra agrupados por Ação e status, para valorar sem carregar os itens. */
    record ServicosDaObra(UUID idObra, UUID idContrato, UUID idAcao, StatusItemPlanejado status, BigDecimal quantidade, long itens) {
    }

    @Query("""
            select new com.nexo.app.modules.obras.infrastructure.persistence.ItemPlanejadoRepository$ServicosDaObra(
                k.idObra, k.idContrato, i.idAcao, i.status, sum(i.quantidadePrevista), count(i))
            from ItemPlanejado i join i.kitAplicado k
            where k.idObra in :ids and i.ativo = true and k.ativo = true
              and i.tipo = com.nexo.app.modules.obras.domain.TipoItemKit.SERVICO
            group by k.idObra, k.idContrato, i.idAcao, i.status
            """)
    List<ServicosDaObra> somarServicos(@Param("ids") Collection<UUID> ids);

    @Query("""
            select distinct k.idObra from ItemPlanejado i join i.kitAplicado k
            where i.ativo = true and k.ativo = true and k.executadoAntes = false
              and i.tipo = com.nexo.app.modules.obras.domain.TipoItemKit.SERVICO
              and i.status = com.nexo.app.modules.obras.domain.StatusItemPlanejado.PENDENTE
            """)
    List<UUID> obrasComServicoPendente();

    @Query("""
            select i from ItemPlanejado i join fetch i.kitAplicado k
            where i.id in :ids and k.idObra = :idObra and i.ativo = true and k.ativo = true
            """)
    List<ItemPlanejado> listarDaObra(@Param("idObra") UUID idObra, @Param("ids") Collection<UUID> ids);

    /** Pontos com algo já feito: execução apontada ou kit marcado como executado antes do sistema. */
    @Query("""
            select distinct k.idPonto from ItemPlanejado i join i.kitAplicado k
            where k.idObra = :idObra and k.idPonto is not null and i.ativo = true and k.ativo = true
              and (i.quantidadeExecutada > 0 or k.executadoAntes = true)
            """)
    List<UUID> pontosComExecucao(@Param("idObra") UUID idObra);

    @Query("""
            select distinct k.idVao from ItemPlanejado i join i.kitAplicado k
            where k.idObra = :idObra and k.idVao is not null and i.ativo = true and k.ativo = true
              and (i.quantidadeExecutada > 0 or k.executadoAntes = true)
            """)
    List<UUID> vaosComExecucao(@Param("idObra") UUID idObra);

    /** Serviços concluídos da obra ainda sem medição, fora os kits executados antes do sistema. */
    @Query("""
            select i from ItemPlanejado i join fetch i.kitAplicado k
            where k.idObra = :idObra and i.ativo = true and k.ativo = true and k.executadoAntes = false
              and i.tipo = com.nexo.app.modules.obras.domain.TipoItemKit.SERVICO
              and i.status = com.nexo.app.modules.obras.domain.StatusItemPlanejado.CONCLUIDO
            """)
    List<ItemPlanejado> listarMediveis(@Param("idObra") UUID idObra);
}
