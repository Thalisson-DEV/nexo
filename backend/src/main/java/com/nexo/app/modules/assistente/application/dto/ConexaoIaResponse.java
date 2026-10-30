package com.nexo.app.modules.assistente.application.dto;

import com.nexo.app.modules.assistente.domain.TipoConexaoIa;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Conexao de IA como aparece na tela "Conectar sua IA".
 *
 * @param situacao      CONECTADA, VENCIDA ou DESCONECTADA
 * @param usuarioLogin  dono da conexao; so preenchido na visao do administrador
 */
public record ConexaoIaResponse(
        UUID id,
        String nome,
        TipoConexaoIa tipo,
        boolean permiteEscrita,
        String situacao,
        String prefixoChave,
        String aplicativo,
        OffsetDateTime criadaEm,
        OffsetDateTime ultimoUsoEm,
        OffsetDateTime expiraEm,
        OffsetDateTime desconectadaEm,
        long totalChamadas,
        UUID usuarioId,
        String usuarioLogin) {
}
