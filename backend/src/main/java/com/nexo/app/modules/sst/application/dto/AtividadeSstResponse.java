package com.nexo.app.modules.sst.application.dto;

import java.util.UUID;

/**
 * Atividade de SST cadastrada. O nome, a função e o processo do gestor são resolvidos
 * na leitura — aqui o vínculo é vivo. É no <b>evento</b> que esses dados viram cópia.
 */
public record AtividadeSstResponse(
        UUID id,
        String nome,
        String descricao,
        UUID idGestor,
        String nomeGestor,
        String funcaoGestor,
        UUID idProcesso,
        String nomeProcesso,
        boolean ativo
) {
}
