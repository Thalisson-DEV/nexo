package com.nexo.app.modules.execucao.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A identificação da nota, fora os serviços e os materiais. Numa correção, o campo
 * nulo fica como está.
 */
public record DadosNota(
        String referenciaNota,
        TipoNota tipoNota,
        UUID idTipoServico,
        UUID idMunicipio,
        OffsetDateTime dataLancamento
) {
}
