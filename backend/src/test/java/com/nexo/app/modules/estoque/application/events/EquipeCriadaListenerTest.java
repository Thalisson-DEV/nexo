package com.nexo.app.modules.estoque.application.events;

import com.nexo.app.modules.estoque.application.usecases.EstoqueCampoEquipeProvisioningService;
import com.nexo.app.shared.domain.events.EquipeCriadaEvent;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EquipeCriadaListenerTest {

    @Test
    void provisionaSincronamenteNaTransacaoDaEquipe() throws NoSuchMethodException {
        EstoqueCampoEquipeProvisioningService service = mock(EstoqueCampoEquipeProvisioningService.class);
        EquipeCriadaListener listener = new EquipeCriadaListener(service);
        EquipeCriadaEvent event = new EquipeCriadaEvent(
                UUID.randomUUID(), "EQ-01", UUID.randomUUID());

        listener.aoCriarEquipe(event);

        verify(service).provisionar(event.idEquipe(), event.codigo(), event.idBaseOperacional());
        Method metodo = EquipeCriadaListener.class.getMethod("aoCriarEquipe", EquipeCriadaEvent.class);
        assertThat(metodo.getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.MANDATORY);
    }

    @Test
    void propagaFalhaParaPermitirRollbackDaCriacaoDaEquipe() {
        EstoqueCampoEquipeProvisioningService service = mock(EstoqueCampoEquipeProvisioningService.class);
        EquipeCriadaListener listener = new EquipeCriadaListener(service);
        EquipeCriadaEvent event = new EquipeCriadaEvent(
                UUID.randomUUID(), "EQ-01", UUID.randomUUID());
        RuntimeException falha = new RuntimeException("falha no estoque");
        doThrow(falha).when(service)
                .provisionar(event.idEquipe(), event.codigo(), event.idBaseOperacional());

        assertThatThrownBy(() -> listener.aoCriarEquipe(event)).isSameAs(falha);
    }
}
