package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.TipoEventoSst;

/**
 * Projeção da contagem de eventos e de dias perdidos por tipo de ocorrência.
 *
 * <p>Serve à métrica oficial de acidentalidade, que precisa de números <b>determinísticos</b>:
 * a agregação acontece no banco, e o assistente recebe o resultado pronto em vez de
 * receber linhas para somar por conta própria.</p>
 */
public record ContagemPorTipoEvento(
        TipoEventoSst tipo,
        long total,
        long diasPerdidos,
        long comCat
) {
}
