package com.nexo.app.modules.execucao.application.dto;

import com.nexo.app.modules.execucao.domain.StatusTurno;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.UUID;

/**
 * Critérios da consulta paginada de turnos. Campos nulos não filtram.
 *
 * @param idsEquipes  equipes aceitas; {@code null} aceita todas e vazio não aceita nenhuma
 * @param aberturaDe  abertura em ou depois deste instante
 * @param aberturaAte abertura antes deste instante (exclusivo)
 * @param ativoDe     com {@code ativoAte}: turnos abertos em algum momento de {@code [ativoDe, ativoAte)}
 * @param ativoAte    fim exclusivo da janela de atividade
 */
public record ConsultaTurnos(
        Collection<UUID> idsEquipes,
        StatusTurno status,
        OffsetDateTime aberturaDe,
        OffsetDateTime aberturaAte,
        OffsetDateTime ativoDe,
        OffsetDateTime ativoAte
) {
}
