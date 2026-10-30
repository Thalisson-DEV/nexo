package com.nexo.app.modules.frota.application.events;

import com.nexo.app.modules.frota.application.usecases.VeiculoFrotaUseCases;
import com.nexo.app.shared.domain.events.VeiculoAlteradoEvent;
import com.nexo.app.shared.domain.events.VeiculoCadastradoEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mantém a ficha da frota junto com o cadastro do RH, na mesma transação: um veículo
 * nunca existe no RH sem ficha, e a placa da ficha nunca diverge da do RH.
 */
@Component
@RequiredArgsConstructor
public class CadastroVeiculoListener {

    private final VeiculoFrotaUseCases veiculos;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void aoCadastrarVeiculo(VeiculoCadastradoEvent event) {
        veiculos.provisionarFicha(event.idVeiculo(), event.placa(), event.idBaseOperacional());
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void aoAlterarVeiculo(VeiculoAlteradoEvent event) {
        veiculos.sincronizarCadastro(event.idVeiculo(), event.placa(), event.idBaseOperacional(), event.ativo());
    }
}
