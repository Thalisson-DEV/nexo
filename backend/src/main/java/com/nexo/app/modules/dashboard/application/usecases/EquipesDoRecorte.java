package com.nexo.app.modules.dashboard.application.usecases;

import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.shared.application.SelecaoMultipla;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Equipes ativas que entram no recorte de base operacional e processo dos dashboards. */
@Component
@RequiredArgsConstructor
class EquipesDoRecorte {

    private final EquipeApi equipeApi;

    /** Seleção vazia (ou nula) não restringe a dimensão. */
    List<EquipeResponse> listar(Collection<UUID> idsBaseOperacional, Collection<UUID> idsProcesso) {
        return equipeApi.listarEquipesAtivas().stream()
                .filter(e -> SelecaoMultipla.contem(idsBaseOperacional,
                        e.baseOperacional() != null ? e.baseOperacional().id() : null))
                .filter(e -> SelecaoMultipla.contem(idsProcesso, e.processo() != null ? e.processo().id() : null))
                .toList();
    }
}
