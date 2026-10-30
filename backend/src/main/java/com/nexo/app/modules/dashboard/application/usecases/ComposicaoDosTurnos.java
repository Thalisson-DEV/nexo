package com.nexo.app.modules.dashboard.application.usecases;

import com.nexo.app.modules.dashboard.application.dto.DetalhamentoTurnoResponse.Composicao;
import com.nexo.app.modules.execucao.application.dto.TurnoResponse;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.modules.rh.ResumoColaborador;
import com.nexo.app.modules.rh.VeiculoApi;
import com.nexo.app.modules.rh.VeiculoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Resolve, para vários turnos de uma vez, a composição registrada na abertura:
 * quem abriu, a placa confirmada e os nomes dos colaboradores.
 *
 * <p>Os nomes dos membros vêm do RH numa consulta só. Placa e nome de quem abriu são
 * resolvidos por item distinto e memorizados durante a montagem — há no máximo um
 * por equipe.</p>
 */
@Component
@RequiredArgsConstructor
class ComposicaoDosTurnos {

    private final ColaboradorApi colaboradorApi;
    private final VeiculoApi veiculoApi;

    Resolvedor para(Collection<TurnoResponse> turnos) {
        List<UUID> idsColaboradores = turnos.stream()
                .filter(t -> t.idsColaboradores() != null)
                .flatMap(t -> t.idsColaboradores().stream())
                .distinct()
                .toList();
        Map<UUID, ResumoColaborador> resumos = idsColaboradores.isEmpty()
                ? Map.of()
                : colaboradorApi.buscarResumosPorIds(idsColaboradores);
        return new Resolvedor(resumos);
    }

    final class Resolvedor {
        private final Map<UUID, ResumoColaborador> resumos;
        private final Map<UUID, String> nomesPorUsuario = new HashMap<>();
        private final Map<UUID, String> placasPorVeiculo = new HashMap<>();

        private Resolvedor(Map<UUID, ResumoColaborador> resumos) {
            this.resumos = resumos;
        }

        Composicao composicao(TurnoResponse turno, EquipeResponse equipe) {
            return new Composicao(abertoPor(turno), placa(turno, equipe), membros(turno));
        }

        private String abertoPor(TurnoResponse turno) {
            if (turno.idUsuarioAbertura() == null) {
                return null;
            }
            return nomesPorUsuario.computeIfAbsent(turno.idUsuarioAbertura(),
                    id -> colaboradorApi.buscarNomeCompletoPorUsuario(id).orElse(null));
        }

        private String placa(TurnoResponse turno, EquipeResponse equipe) {
            VeiculoResponse veiculoDaEquipe = equipe != null ? equipe.veiculo() : null;
            if (turno.idVeiculo() == null) {
                return veiculoDaEquipe != null ? veiculoDaEquipe.placa() : null;
            }
            if (veiculoDaEquipe != null && turno.idVeiculo().equals(veiculoDaEquipe.id())) {
                return veiculoDaEquipe.placa();
            }
            return placasPorVeiculo.computeIfAbsent(turno.idVeiculo(),
                    id -> veiculoApi.buscarPorIdResponse(id).map(VeiculoResponse::placa).orElse(null));
        }

        private List<String> membros(TurnoResponse turno) {
            if (turno.idsColaboradores() == null) {
                return List.of();
            }
            return turno.idsColaboradores().stream()
                    .map(resumos::get)
                    .filter(Objects::nonNull)
                    .map(ResumoColaborador::nomeCompleto)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(String::toLowerCase))
                    .toList();
        }
    }
}
