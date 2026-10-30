package com.nexo.app.modules.estoque.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record PayloadEventoSolicitacao(
        String comando,
        List<AlteracaoEventoSolicitacao> alteracoes,
        Map<String, Object> snapshots
) {
    public PayloadEventoSolicitacao {
        Objects.requireNonNull(comando, "O comando do evento e obrigatorio");
        alteracoes = alteracoes == null
                ? List.of()
                : alteracoes.stream()
                        .sorted(Comparator.comparing(AlteracaoEventoSolicitacao::caminho))
                        .toList();
        snapshots = snapshots == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new TreeMap<>(snapshots));
    }

    public static PayloadEventoSolicitacao semAlteracoes(
            String comando,
            Map<String, Object> snapshots
    ) {
        return new PayloadEventoSolicitacao(comando, List.of(), snapshots);
    }
}
