package com.nexo.app.modules.execucao.application.dto;

import com.nexo.app.modules.execucao.domain.DadosNota;
import com.nexo.app.modules.execucao.domain.TipoNota;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Como a nota deve ficar depois da correção: a lista completa de serviços e de
 * materiais, não só o que mudou. O servidor compara com o que está gravado, registra
 * a diferença na linha do tempo e ajusta o saldo da equipe.
 *
 * <p>{@code idOperacao} e {@code alteradoEm} vêm do app de campo: a alteração pode
 * ter sido feita offline e chegar depois. O mesmo {@code idOperacao} reenviado não
 * altera a nota de novo.</p>
 *
 * <p>Os dados da nota são opcionais e o nulo fica como está, então a fila de versões
 * antigas do app continua valendo. O campo corrige só o número; tipo, tipo de serviço,
 * município e data são do escritório.</p>
 */
public record AlterarExecucaoRequest(
        List<UUID> acoes,

        @Valid
        List<RegistrarExecucaoRequest.MaterialItem> materiais,

        @NotBlank(message = "Informe o motivo da alteração.")
        @Size(max = 500, message = "O motivo pode ter no máximo 500 caracteres.")
        String motivo,

        UUID idOperacao,

        OffsetDateTime alteradoEm,

        @Size(max = 100, message = "O número da nota pode ter no máximo 100 caracteres.")
        String referenciaNota,

        TipoNota tipoNota,

        UUID idTipoServico,

        UUID idMunicipio,

        OffsetDateTime dataLancamento
) {
    public AlterarExecucaoRequest {
        acoes = acoes == null ? List.of() : acoes;
        materiais = materiais == null ? List.of() : materiais;
    }

    /** Só serviços e materiais. */
    public AlterarExecucaoRequest(List<UUID> acoes, List<RegistrarExecucaoRequest.MaterialItem> materiais, String motivo,
                                  UUID idOperacao, OffsetDateTime alteradoEm) {
        this(acoes, materiais, motivo, idOperacao, alteradoEm, null, null, null, null, null);
    }

    public DadosNota dados() {
        return new DadosNota(referenciaNota, tipoNota, idTipoServico, idMunicipio, dataLancamento);
    }
}
