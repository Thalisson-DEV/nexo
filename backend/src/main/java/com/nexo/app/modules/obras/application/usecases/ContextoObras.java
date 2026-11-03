package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.dto.MedicaoResponses;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.MunicipioApi;
import com.nexo.app.modules.rh.SupervisorApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Base, município, supervisor, status e prioridade de várias obras, com os nomes resolvidos uma vez por id, para as
 * listas de medição filtrarem e mostrarem a obra no mesmo recorte da carteira.
 */
@Component
@RequiredArgsConstructor
class ContextoObras {

    private final BaseOperacionalApi baseApi;
    private final MunicipioApi municipioApi;
    private final SupervisorApi supervisorApi;

    Map<UUID, MedicaoResponses.ContextoObra> de(Collection<Obra> obras) {
        Map<UUID, String> bases = new HashMap<>();
        Map<UUID, String> municipios = new HashMap<>();
        Map<UUID, String> supervisores = new HashMap<>();
        obras.stream().map(Obra::getIdBaseOperacional).filter(Objects::nonNull).distinct()
                .forEach(id -> bases.put(id, baseApi.buscarPorIdResponse(id).map(b -> b.nome()).orElse(null)));
        obras.stream().map(Obra::getIdMunicipio).filter(Objects::nonNull).distinct()
                .forEach(id -> municipios.put(id, municipioApi.buscarPorIdResponse(id).map(m -> m.nome()).orElse(null)));
        obras.stream().map(Obra::getIdSupervisor).filter(Objects::nonNull).distinct()
                .forEach(id -> supervisores.put(id, supervisorApi.buscarPorIdResponse(id)
                        .map(s -> s.colaborador() != null ? s.colaborador().nomeCompleto() : null).orElse(null)));
        Map<UUID, MedicaoResponses.ContextoObra> contextos = new HashMap<>();
        for (Obra o : obras) {
            contextos.put(o.getId(), new MedicaoResponses.ContextoObra(
                    o.getIdBaseOperacional(), bases.get(o.getIdBaseOperacional()),
                    o.getIdMunicipio(), municipios.get(o.getIdMunicipio()),
                    o.getIdSupervisor(), supervisores.get(o.getIdSupervisor()),
                    o.getStatusObra() != null ? o.getStatusObra().getNome() : null,
                    o.getPrioridade() != null ? o.getPrioridade().name() : null));
        }
        return contextos;
    }
}
