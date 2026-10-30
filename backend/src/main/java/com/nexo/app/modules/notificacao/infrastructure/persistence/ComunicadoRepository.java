package com.nexo.app.modules.notificacao.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexo.app.modules.notificacao.domain.Comunicado;
import com.nexo.app.modules.notificacao.domain.TipoAlvoSegmento;

public interface ComunicadoRepository
        extends JpaRepository<Comunicado, UUID>, JpaSpecificationExecutor<Comunicado> {

    /**
     * O mural de um usuário, resolvido no banco.
     *
     * <p>Três condições em uma consulta só: está no ar (publicado e dentro da
     * janela), alcança este usuário (algum segmento bate com os atributos dele) e
     * ele ainda não o tirou da frente (não dispensou nem está com adiamento
     * vigente). Fazer isso em memória exigiria trazer todos os comunicados no ar e
     * todas as interações do usuário a cada abertura da aplicação.</p>
     *
     * <p>Os tipos de alvo entram por parâmetro em vez de literal para não depender
     * de como o Hibernate resolve constantes de enumeração dentro de HQL.</p>
     */
    @Query("""
            SELECT DISTINCT c FROM Comunicado c
             WHERE c.ativo = true
               AND c.status = com.nexo.app.modules.notificacao.domain.StatusComunicado.PUBLICADO
               AND c.dataInicio <= :agora
               AND (c.dataFim IS NULL OR c.dataFim > :agora)
               AND EXISTS (
                    SELECT 1 FROM SegmentoComunicado s
                     WHERE s.comunicado = c AND s.ativo = true
                       AND (s.tipoAlvo = :alvoTodos
                            OR (s.tipoAlvo = :alvoPerfil AND s.idAlvo = :idPerfil)
                            OR (s.tipoAlvo = :alvoUsuario AND s.idAlvo = :idUsuario)
                            OR (s.tipoAlvo = :alvoTipo AND s.valorAlvo = :tipoUsuario)))
               AND NOT EXISTS (
                    SELECT 1 FROM InteracaoComunicado i
                     WHERE i.comunicado = c AND i.ativo = true AND i.idUsuario = :idUsuario
                       AND (i.dispensadoEm IS NOT NULL
                            OR (i.adiadoAte IS NOT NULL AND i.adiadoAte > :agora)))
             ORDER BY c.fixado DESC, c.dataInicio DESC
            """)
    List<Comunicado> buscarMuralDoUsuario(@Param("idUsuario") UUID idUsuario,
                                          @Param("idPerfil") UUID idPerfil,
                                          @Param("tipoUsuario") String tipoUsuario,
                                          @Param("agora") OffsetDateTime agora,
                                          @Param("alvoTodos") TipoAlvoSegmento alvoTodos,
                                          @Param("alvoPerfil") TipoAlvoSegmento alvoPerfil,
                                          @Param("alvoUsuario") TipoAlvoSegmento alvoUsuario,
                                          @Param("alvoTipo") TipoAlvoSegmento alvoTipo);
}
