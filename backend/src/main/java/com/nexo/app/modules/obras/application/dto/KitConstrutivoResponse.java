package com.nexo.app.modules.obras.application.dto;

import com.nexo.app.modules.obras.domain.ClasseTensao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Kit com valor em R$ calculado na {@code dataReferenciaValor}: soma de quantidade × valor vigente das ações dos
 * itens de serviço. {@code servicosSemValor} conta os serviços sem valor vigente, que ficam fora da soma.
 */
public record KitConstrutivoResponse(
        UUID id,
        UUID idContrato,
        String numeroContrato,
        String codigo,
        String descricao,
        String grupo,
        Set<ClasseTensao> classesTensao,
        int quantidadeItens,
        int quantidadeServicos,
        int quantidadeMateriais,
        BigDecimal valor,
        int servicosSemValor,
        LocalDate dataReferenciaValor,
        List<ItemKitResponse> itens,
        boolean ativo,
        Long versao,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataUltimaAlteracao
) {
}
