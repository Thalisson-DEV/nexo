package com.nexo.app.modules.frota.application.dto;

import com.nexo.app.modules.frota.domain.CategoriaCnh;
import com.nexo.app.modules.frota.domain.SituacaoVencimento;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Condutor com a CNH mascarada e o veículo que conduz agora. */
public record CondutorResponse(
        UUID id,
        UUID idColaborador,
        String nome,
        String funcao,
        String baseOperacional,
        String cnhNumeroMascarado,
        CategoriaCnh cnhCategoria,
        LocalDate cnhValidade,
        LocalDate cnhPrimeiraHabilitacao,
        SituacaoVencimento situacaoCnh,
        Long diasParaVencerCnh,
        UUID idVeiculoAtual,
        String placaAtual,
        OffsetDateTime conduzDesde,
        String observacao,
        boolean ativo
) {
}
