package com.nexo.app.modules.estoque.application.events;

import com.nexo.app.modules.estoque.application.usecases.EstoqueCampoEquipeProvisioningService;
import com.nexo.app.shared.domain.events.EquipeCriadaEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class EquipeCriadaListener {

    private final EstoqueCampoEquipeProvisioningService provisioningService;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void aoCriarEquipe(EquipeCriadaEvent event) {
        provisioningService.provisionar(event.idEquipe(), event.codigo(), event.idBaseOperacional());
    }
}
