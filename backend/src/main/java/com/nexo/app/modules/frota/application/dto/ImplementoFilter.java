package com.nexo.app.modules.frota.application.dto;

import com.nexo.app.modules.frota.domain.SituacaoOperacional;
import com.nexo.app.shared.application.SelecaoMultipla;

import java.util.List;
import java.util.UUID;

/**
 * Filtro de implementos. {@code idVeiculo} traz os acoplados a ele agora;
 * {@code acoplado} separa os que estão em algum veículo dos que estão livres.
 */
public record ImplementoFilter(
        String busca,
        List<UUID> idsBase,
        List<UUID> idsTipoImplemento,
        List<SituacaoOperacional> situacoes,
        UUID idVeiculo,
        Boolean acoplado,
        Boolean ativo
) {
    public ImplementoFilter {
        idsBase = SelecaoMultipla.normalizar(idsBase);
        idsTipoImplemento = SelecaoMultipla.normalizar(idsTipoImplemento);
        situacoes = SelecaoMultipla.normalizar(situacoes);
    }
}
