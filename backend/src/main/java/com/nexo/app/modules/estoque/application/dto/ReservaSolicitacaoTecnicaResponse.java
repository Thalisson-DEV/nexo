package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.domain.ReservaSolicitacaoTecnica;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public record ReservaSolicitacaoTecnicaResponse(
        UUID id,
        UUID idSolicitacao,
        String numero,
        LocalDate dataCriacao,
        UUID idAgenteResponsavel,
        String nomeAgente,
        String empresaAgente,
        OffsetDateTime registradaEm,
        UUID idUsuarioRegistro,
        OffsetDateTime atualizadaEm,
        UUID idUsuarioAtualizacao,
        long versao
) {
    public static ReservaSolicitacaoTecnicaResponse de(ReservaSolicitacaoTecnica reserva) {
        return new ReservaSolicitacaoTecnicaResponse(
                reserva.getId(), reserva.getSolicitacao().getId(), reserva.getNumeroApi(),
                reserva.getDataCriacao(), reserva.getIdAgenteResponsavel(),
                reserva.getNomeAgenteSnapshot(), reserva.getEmpresaAgenteSnapshot(),
                utc(reserva.getRegistradaEm()), reserva.getIdUsuarioRegistro(),
                utc(reserva.getAtualizadaEm()), reserva.getIdUsuarioAtualizacao(), reserva.getVersao());
    }

    private static OffsetDateTime utc(OffsetDateTime valor) {
        return valor == null ? null : valor.withOffsetSameInstant(ZoneOffset.UTC);
    }
}
