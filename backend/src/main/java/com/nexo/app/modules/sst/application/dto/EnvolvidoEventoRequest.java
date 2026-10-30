package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.PapelEnvolvido;
import com.nexo.app.modules.sst.domain.TipoEnvolvido;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Uma pessoa envolvida no evento.
 *
 * <p>Para {@code COLABORADOR} basta o id: nome, função e base são copiados do RH no
 * momento do registro, e não do que o cliente mandar — o snapshot precisa ser fiel ao
 * cadastro, não ao formulário. Para {@code TERCEIRO} valem o nome e a empresa
 * informados, porque não há cadastro de onde copiar.</p>
 */
public record EnvolvidoEventoRequest(

        @NotNull(message = "Informe se a pessoa é colaborador ou terceiro")
        TipoEnvolvido tipo,

        @NotNull(message = "O papel do envolvido é obrigatório")
        PapelEnvolvido papel,

        @Schema(description = "Obrigatório para COLABORADOR; proibido para TERCEIRO")
        UUID idColaborador,

        @Schema(description = "Obrigatório para TERCEIRO; ignorado para COLABORADOR (copiado do RH)")
        @Size(max = 255)
        String nome,

        @Size(max = 120)
        String funcao,

        @Schema(description = "Obrigatória para TERCEIRO: sem cadastro, é ela que identifica a pessoa")
        @Size(max = 150)
        String empresa
) {
}
