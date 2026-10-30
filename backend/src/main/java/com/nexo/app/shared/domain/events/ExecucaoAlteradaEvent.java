package com.nexo.app.shared.domain.events;

import java.util.List;
import java.util.UUID;

/**
 * Evento disparado quando os materiais de uma nota já sincronizada são corrigidos.
 * Só leva o material da equipe: o estoque estorna o que saiu da nota e baixa o que
 * entrou, como se a nota tivesse sido lançada assim.
 */
public record ExecucaoAlteradaEvent(
    UUID idExecucao,
    UUID idEquipe,
    UUID idUsuarioOperador,
    /* Saíram da nota: a aplicação volta ao saldo NOVO, a retirada sai da SUCATA. */
    List<ExecucaoConcluidaEvent.ItemMaterialEvent> estornos,
    /* Entraram na nota: tratados como na conclusão (aplicação baixa, retirada entra como sucata). */
    List<ExecucaoConcluidaEvent.ItemMaterialEvent> lancamentos
) {
}
