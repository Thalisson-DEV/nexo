package com.nexo.app.modules.iam.application.usecases;

import com.nexo.app.modules.iam.application.dto.HistoricoRegistroResponse;
import com.nexo.app.modules.iam.domain.LogSistema;
import com.nexo.app.modules.iam.infrastructure.persistence.LogSistemaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class BuscarHistoricoRegistroUseCase {

    private final LogSistemaRepository repository;

    private static final Set<String> CAMPOS_IGNORADOS = Set.of(
        "snapshotAnterior", "versao", "dataUltimaAlteracao", "usuarioUltimaAlteracao"
    );

    @Transactional(readOnly = true)
    public HistoricoRegistroResponse executar(UUID registroId) {
        List<LogSistema> logs = repository.findByRegistroIdOrderByDataCriacaoDesc(registroId);
        
        if (logs.isEmpty()) {
            return new HistoricoRegistroResponse(registroId, "DESCONHECIDA", Collections.emptyList());
        }

        String tabela = logs.get(0).getTabela();
        List<HistoricoRegistroResponse.LogEntryResponse> alteracoes = logs.stream()
            .map(this::mapearParaLogEntry)
            .toList();

        return new HistoricoRegistroResponse(registroId, tabela, alteracoes);
    }

    private HistoricoRegistroResponse.LogEntryResponse mapearParaLogEntry(LogSistema log) {
        List<HistoricoRegistroResponse.AlteracaoCampo> camposAlterados = new ArrayList<>();

        Map<String, Object> anterior = log.getDadosAnteriores() != null ? log.getDadosAnteriores() : Collections.emptyMap();
        Map<String, Object> novo = log.getDadosNovos() != null ? log.getDadosNovos() : Collections.emptyMap();

        if ("INSERT".equals(log.getOperacao())) {
            novo.forEach((k, v) -> {
                if (!CAMPOS_IGNORADOS.contains(k) && v != null) {
                    camposAlterados.add(new HistoricoRegistroResponse.AlteracaoCampo(k, null, v));
                }
            });
        } else if ("DELETE".equals(log.getOperacao())) {
            anterior.forEach((k, v) -> {
                if (!CAMPOS_IGNORADOS.contains(k) && v != null) {
                    camposAlterados.add(new HistoricoRegistroResponse.AlteracaoCampo(k, v, null));
                }
            });
        } else {
            // UPDATE: Compara o que mudou
            Set<String> todosCampos = new HashSet<>(anterior.keySet());
            todosCampos.addAll(novo.keySet());

            for (String campo : todosCampos) {
                if (CAMPOS_IGNORADOS.contains(campo)) continue;

                Object valAnt = anterior.get(campo);
                Object valNov = novo.get(campo);

                if (!Objects.equals(valAnt, valNov)) {
                    camposAlterados.add(new HistoricoRegistroResponse.AlteracaoCampo(campo, valAnt, valNov));
                }
            }
        }

        return new HistoricoRegistroResponse.LogEntryResponse(
            log.getId(),
            log.getDataCriacao(),
            log.getOperacao(),
            log.getUsuarioCriacao(),
            camposAlterados
        );
    }
}
