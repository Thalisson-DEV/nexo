package com.nexo.app.modules.rh;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * API pública do RH para as metas de faturamento das equipes, consumida pelos
 * dashboards de execução.
 */
public interface MetaFaturamentoApi {

    /** Cronograma das metas ativas da equipe; vazio quando ela não tem meta. */
    CronogramaMetaFaturamento cronograma(UUID idEquipe);

    /** Cronogramas das equipes informadas; equipes sem meta recebem um cronograma vazio. */
    Map<UUID, CronogramaMetaFaturamento> cronogramas(Collection<UUID> idsEquipes);
}
