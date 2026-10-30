package com.nexo.app.modules.execucao.application.dto;

import java.util.List;
import java.util.UUID;

/**
 * Abertura de turno. O responsável pode confirmar a composição da equipe no mesmo
 * passo: {@code idVeiculo} troca o veículo e {@code idsColaboradores} define a lista
 * completa de parceiros. Campos nulos mantêm a composição cadastrada.
 */
public record AbrirTurnoRequest(
    UUID idEquipe,
    UUID idVeiculo,
    List<UUID> idsColaboradores
) {
    public AbrirTurnoRequest(UUID idEquipe) {
        this(idEquipe, null, null);
    }
}
