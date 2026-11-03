package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.Programacao;
import com.nexo.app.modules.obras.domain.TurnoProgramacao;
import org.springframework.data.jpa.repository.JpaRepository;
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
public interface ProgramacaoRepository extends JpaRepository<Programacao, UUID> {

    /** Valor congelado de uma programação aberta, sem carregar os itens. */
    record ValorProgramado(UUID idProgramacao, long numero, UUID idObra, UUID idEquipe, TurnoProgramacao turno, LocalDate inicio, LocalDate fim, BigDecimal valor) {
    }

    @Query(value = "select nextval('programacoes_numero_seq')", nativeQuery = true)
    long proximoNumero();

    Optional<Programacao> findByIdAndAtivoTrue(UUID id);

    @Query("""
            select distinct p from Programacao p left join fetch p.itens i left join fetch i.itemPlanejado
            where p.idObra = :idObra and p.ativo = true
            order by p.numero desc
            """)
    List<Programacao> listarDaObraComItens(@Param("idObra") UUID idObra);

    /** Inclui as canceladas: o Gantt filtra pelo que quer mostrar. */
    @Query("""
            select distinct p from Programacao p left join fetch p.itens i left join fetch i.itemPlanejado
            where p.ativo = true and p.dataInicio <= :fim and p.dataFim >= :inicio
            """)
    List<Programacao> listarNoPeriodoComItens(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    /** Abertas que terminaram antes da data: atraso de períodos anteriores não pode sumir da tela. */
    @Query("""
            select distinct p from Programacao p left join fetch p.itens i left join fetch i.itemPlanejado
            where p.ativo = true and p.status = com.nexo.app.modules.obras.domain.StatusProgramacao.PROGRAMADA
              and p.dataFim < :data
            """)
    List<Programacao> listarAbertasTerminadasAntes(@Param("data") LocalDate data);

    @Query("""
            select new com.nexo.app.modules.obras.infrastructure.persistence.ProgramacaoRepository$ValorProgramado(
                p.id, p.numero, p.idObra, p.idEquipe, p.turno, p.dataInicio, p.dataFim, coalesce(sum(i.valorTotal), 0))
            from Programacao p left join p.itens i on i.ativo = true
            where p.ativo = true and p.status = com.nexo.app.modules.obras.domain.StatusProgramacao.PROGRAMADA
              and p.idEquipe in :idsEquipes and p.dataInicio <= :fim and p.dataFim >= :inicio
            group by p.id, p.numero, p.idObra, p.idEquipe, p.turno, p.dataInicio, p.dataFim
            """)
    List<ValorProgramado> somarNoPeriodo(@Param("idsEquipes") Collection<UUID> idsEquipes,
                                         @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}
