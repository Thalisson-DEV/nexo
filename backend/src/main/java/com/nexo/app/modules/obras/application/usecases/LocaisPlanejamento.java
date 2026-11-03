package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.domain.PontoPlanejamento;
import com.nexo.app.modules.obras.domain.VaoPlanejamento;
import com.nexo.app.modules.obras.infrastructure.persistence.PontoPlanejamentoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.VaoPlanejamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Nome legível de pontos e vãos de uma obra para listas e relatórios: "Folha 1 · P18" e "Vão P17–P18".
 */
@Component
@RequiredArgsConstructor
class LocaisPlanejamento {

    private final PontoPlanejamentoRepository pontoRepository;
    private final VaoPlanejamentoRepository vaoRepository;

    /** Pontos e vãos ativos da obra, pelo id. */
    Map<UUID, String> daObra(UUID idObra) {
        Map<UUID, PontoPlanejamento> pontos = new HashMap<>();
        pontoRepository.findByIdObraAndAtivoTrueOrderByPaginaAscNumeroAsc(idObra).forEach(p -> pontos.put(p.getId(), p));
        Map<UUID, String> locais = new HashMap<>();
        pontos.values().forEach(p -> locais.put(p.getId(), "Folha " + p.getPagina() + " · " + p.getNumero()));
        for (VaoPlanejamento vao : vaoRepository.findByIdObraAndAtivoTrue(idObra)) {
            locais.put(vao.getId(), "Vão " + numero(pontos.get(vao.getIdPontoA())) + "–" + numero(pontos.get(vao.getIdPontoB())));
        }
        return locais;
    }

    static String local(Map<UUID, String> locais, UUID idPonto, UUID idVao) {
        UUID id = idPonto != null ? idPonto : idVao;
        return id != null ? locais.get(id) : null;
    }

    private static String numero(PontoPlanejamento ponto) {
        return ponto != null ? ponto.getNumero() : "?";
    }
}
