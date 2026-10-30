package com.nexo.app.modules.execucao;

import com.nexo.app.modules.execucao.application.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TurnoApi {

    TurnoResponse abrir(AbrirTurnoRequest request);

    ComposicaoTurnoResponse buscarComposicaoMinhaEquipe();

    void fechar(UUID id);

    Optional<TurnoResponse> buscarAtivoPorEquipe(UUID idEquipe);

    TurnoResponse buscarPorId(UUID id);

    /**
     * Turnos das equipes informadas que estiveram abertos em algum momento de
     * {@code [inicio, fim)}: abertos antes do fim e ainda abertos ou fechados depois
     * do início. Um turno esquecido aberto aparece em todos os dias até ser fechado.
     */
    List<TurnoResponse> listarSobrepostosAoPeriodo(OffsetDateTime inicio, OffsetDateTime fim, Collection<UUID> idsEquipes);

    /** Consulta paginada de turnos ativos pelos critérios informados. */
    Page<TurnoResponse> consultar(ConsultaTurnos consulta, Pageable pageable);
}
