package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.StatusModeloAtaSst;
import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ModeloAtaSstDto {
    private ModeloAtaSstDto() {}

    public record Request(
            @NotBlank @Size(max = 160) String nome,
            @NotNull TipoCorpoGovernancaSst tipoCorpo,
            UUID idBaseAplicavel,
            @Size(max = 2_000) String orientacoesGerais) {}

    public record AtualizarRequest(
            @NotBlank @Size(max = 160) String nome,
            @Size(max = 2_000) String orientacoesGerais) {}

    public record Response(
            UUID id,
            UUID familiaId,
            int revisao,
            UUID idRevisaoAnterior,
            UUID idModeloDuplicado,
            TipoCorpoGovernancaSst tipoCorpo,
            UUID idBaseAplicavel,
            String nome,
            String orientacoesGerais,
            StatusModeloAtaSst status,
            boolean modeloInicial,
            UUID idArquivoDocx,
            String nomeArquivoDocx,
            String hashArquivoDocx,
            String statusValidacaoDocx,
            List<String> errosValidacaoDocx,
            Integer slotsMapeados,
            List<String> slotsObrigatoriosPendentes,
            boolean modeloPublicavel,
            OffsetDateTime publicadoEm,
            OffsetDateTime retiradoEm,
            Long versao,
            OffsetDateTime dataCriacao) {}


    public record ArquivoResponse(UUID id, String nomeArquivo, String contentType,
                                  long tamanhoBytes, String sha256, boolean validado) {}
}
