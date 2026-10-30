package com.nexo.app.modules.rh;

import java.util.UUID;

/**
 * Retrato mínimo de um colaborador ativo para seleção por outros módulos.
 *
 * <p>O contrato não expõe CPF, dados contratuais ou entidades JPA. Função e base vêm
 * com seus identificadores para que o consumidor possa validar o escopo sem consultas
 * adicionais por linha.</p>
 */
public record ColaboradorAtivoResumo(
        UUID id,
        String nomeCompleto,
        UUID idFuncao,
        String funcao,
        UUID idBaseOperacional,
        String baseOperacional
) {
}
