package com.nexo.app.modules.frota.application.usecases;

import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.BaseOperacionalResponse;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ResumoColaborador;
import com.nexo.app.modules.rh.VeiculoApi;
import com.nexo.app.modules.rh.VeiculoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Resolve, em lote, os nomes que a frota exibe mas que pertencem a outros módulos
 * (bases, colaboradores, veículos e usuários). Uma consulta por tipo por página,
 * nunca uma por linha.
 */
@Component
@RequiredArgsConstructor
class ReferenciasFrota {

    private final BaseOperacionalApi baseApi;
    private final ColaboradorApi colaboradorApi;
    private final VeiculoApi veiculoApi;
    private final UsuarioApi usuarioApi;

    Map<UUID, String> nomesBases(Collection<UUID> ids) {
        var validos = semNulos(ids);
        if (validos.isEmpty()) return Map.of();
        return baseApi.buscarPorIdsResponse(validos).entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().nome()));
    }

    Map<UUID, ResumoColaborador> colaboradores(Collection<UUID> ids) {
        var validos = semNulos(ids);
        return validos.isEmpty() ? Map.of() : colaboradorApi.buscarResumosPorIds(validos);
    }

    Map<UUID, String> nomesColaboradores(Collection<UUID> ids) {
        return colaboradores(ids).entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().nomeCompleto()));
    }

    Map<UUID, VeiculoResponse> veiculos(Collection<UUID> ids) {
        var validos = semNulos(ids);
        return validos.isEmpty() ? Map.of() : veiculoApi.buscarPorIdsResponse(validos);
    }

    Map<UUID, String> loginsUsuarios(Collection<UUID> ids) {
        var validos = semNulos(ids);
        return validos.isEmpty() ? Map.of() : usuarioApi.buscarLoginsPorIds(validos);
    }

    boolean baseExiste(UUID idBase) {
        return idBase != null && baseApi.existeBase(idBase);
    }

    boolean colaboradorAtivo(UUID idColaborador) {
        return idColaborador != null && colaboradorApi.existeColaborador(idColaborador);
    }

    List<UUID> idsColaboradoresPorNome(String trecho) {
        return colaboradorApi.buscarIdsPorTrechoDoNome(trecho);
    }

    List<UUID> idsColaboradoresPorBases(Collection<UUID> idsBase) {
        return colaboradorApi.listarAtivosPorEscopo(idsBase, null).stream().map(c -> c.id()).toList();
    }

    BaseOperacionalResponse base(UUID idBase) {
        return idBase == null ? null : baseApi.buscarPorIdResponse(idBase).orElse(null);
    }

    private static List<UUID> semNulos(Collection<UUID> ids) {
        return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
    }
}
