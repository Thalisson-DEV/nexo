package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.StatusEventoSst;
import com.nexo.app.modules.sst.domain.TipoEventoSst;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A linha da listagem de eventos.
 *
 * <p>Tudo o que ela mostra já está no próprio evento — atividade, gestor, base e equipe
 * são snapshots. É a consequência prática da regra de cópia: uma página inteira é
 * renderizada sem uma única ida ao RH, e o N+1 que a listagem de desvios precisa
 * contornar simplesmente não existe aqui. Só o nome da classificação é resolvido, em
 * lote, porque catálogo é referência viva.</p>
 */
public record EventoSstResumoResponse(
        UUID id,
        String numeroRegistro,
        TipoEventoSst tipo,
        StatusEventoSst status,

        OffsetDateTime dataOcorrencia,
        OffsetDateTime dataComunicacao,
        long horasAteComunicacao,
        boolean comunicacaoAtrasada,

        String atividadeNome,
        String gestorNome,
        String baseNome,
        String equipeCodigo,
        String veiculoPlaca,

        UUID idClassificacao,
        String classificacaoNome,

        boolean comLesao,

        @Schema(description = "Quantos envolvidos entraram como acidentados")
        int quantidadeAcidentados,

        Integer diasPerdidos,
        boolean possuiCat,
        String localOcorrencia,
        boolean ativo
) {
}
