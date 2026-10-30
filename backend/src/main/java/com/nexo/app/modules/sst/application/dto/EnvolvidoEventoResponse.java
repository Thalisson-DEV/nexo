package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.PapelEnvolvido;
import com.nexo.app.modules.sst.domain.TipoEnvolvido;

import java.util.UUID;

/**
 * Pessoa envolvida no evento, como estava no registro.
 *
 * <p>Os campos vêm do snapshot, não do cadastro atual: um colaborador desligado
 * continua aparecendo aqui com a função e a base que tinha no dia do evento.</p>
 */
public record EnvolvidoEventoResponse(
        UUID id,
        TipoEnvolvido tipo,
        PapelEnvolvido papel,
        UUID idColaborador,
        String nome,
        String funcao,
        String baseOperacional,
        String empresa
) {
}
