package com.nexo.app.modules.estoque.application.services;

import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.shared.domain.events.SolicitacaoMaterialTecnicaEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicadorEventoSolicitacaoTecnica {

    private final ApplicationEventPublisher publisher;

    public void publicarFilho(
            SolicitacaoMaterialTecnicaEvent.Tipo tipo,
            SolicitacaoMaterial solicitacao
    ) {
        publicarFilho(tipo, solicitacao, null, null);
    }

    public void publicarFilho(
            SolicitacaoMaterialTecnicaEvent.Tipo tipo,
            SolicitacaoMaterial solicitacao,
            UUID idOperacao,
            UUID idOperacaoOriginal
    ) {
        publisher.publishEvent(new SolicitacaoMaterialTecnicaEvent(
                tipo,
                solicitacao.getId(),
                solicitacao.getTipoTecnico().getCodigo(),
                solicitacao.getTipoObraSnapshot().name(),
                solicitacao.getOcorrencia(),
                solicitacao.getIdObra(),
                solicitacao.getIdEquipeSaque(),
                solicitacao.getAlmoxarifadoAtendimento().getId(),
                solicitacao.getStatusSeparacao() == null
                        ? null : solicitacao.getStatusSeparacao().name(),
                solicitacao.getReserva() == null
                        ? null : solicitacao.getReserva().getNumeroApi(),
                idOperacao,
                idOperacaoOriginal));
    }
}
