package com.nexo.app.modules.dashboard.application.usecases;

import com.nexo.app.modules.rh.CronogramaMetaFaturamento;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.modules.rh.MetaFaturamentoApi;
import com.nexo.app.modules.rh.PeriodicidadeMetaFaturamento;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Dublê da API de metas para os testes dos dashboards: usa o
 * {@code metaFaturamentoMensal} das equipes de teste como uma meta MENSAL sem prazo.
 */
final class MetasMensais implements MetaFaturamentoApi {

    private final Function<UUID, Optional<EquipeResponse>> equipe;

    private MetasMensais(Function<UUID, Optional<EquipeResponse>> equipe) {
        this.equipe = equipe;
    }

    /** Lê as equipes de uma lista (normalmente o retorno de um mock). */
    static MetasMensais das(Supplier<List<EquipeResponse>> equipes) {
        return new MetasMensais(id -> equipes.get().stream().filter(e -> e.id().equals(id)).findFirst());
    }

    static MetasMensais porEquipe(Function<UUID, Optional<EquipeResponse>> equipe) {
        return new MetasMensais(equipe);
    }

    @Override
    public CronogramaMetaFaturamento cronograma(UUID idEquipe) {
        BigDecimal meta = equipe.apply(idEquipe).map(EquipeResponse::metaFaturamentoMensal).orElse(null);
        return meta == null ? CronogramaMetaFaturamento.vazio()
                : CronogramaMetaFaturamento.constante(meta, PeriodicidadeMetaFaturamento.MENSAL);
    }

    @Override
    public Map<UUID, CronogramaMetaFaturamento> cronogramas(Collection<UUID> idsEquipes) {
        Map<UUID, CronogramaMetaFaturamento> resultado = new HashMap<>();
        idsEquipes.forEach(id -> resultado.put(id, cronograma(id)));
        return resultado;
    }
}
