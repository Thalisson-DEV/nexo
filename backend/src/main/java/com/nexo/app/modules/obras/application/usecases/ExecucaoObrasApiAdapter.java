package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.ExecucaoObrasApi;
import com.nexo.app.modules.obras.infrastructure.persistence.ApontamentoExecucaoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExecucaoObrasApiAdapter implements ExecucaoObrasApi {

    private final ObraRepository obraRepository;
    private final ApontamentoExecucaoRepository apontamentoRepository;

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> processosComObras() {
        return Set.copyOf(obraRepository.listarProcessosComObras());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExecutadoNoDia> executadoPorDia(Collection<UUID> idsEquipes, LocalDate de, LocalDate ate) {
        if (idsEquipes == null || idsEquipes.isEmpty()) {
            return List.of();
        }
        return apontamentoRepository.somarPorObraEquipeEData(idsEquipes, de, ate).stream()
                .map(e -> new ExecutadoNoDia(e.idObra(), e.numero(), e.titulo(), e.idEquipe(), e.data(),
                        e.valor(), e.servicos()))
                .toList();
    }
}
